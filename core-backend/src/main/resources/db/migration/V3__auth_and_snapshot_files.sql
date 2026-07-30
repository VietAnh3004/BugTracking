-- Tuần 5-7: người dùng + JWT auth + upload snapshot theo từng file trên object storage.
--
-- Append-only, không đụng V1/V2. Mọi CHECK cho cột kiểu liệt kê viết INLINE KHÔNG ĐẶT TÊN để
-- Postgres tự sinh tên '<table>_<column>_check' — EnumCheckConstraintTest tra cứu đúng theo quy
-- ước đó (xem ADR 0004 và test cùng tên).

-- ==========================================================================================
-- app_user — tên "app_user" chứ không phải "user": USER là từ khoá dành riêng của Postgres
-- (SELECT user; trả về vai trò hiện tại), đặt tên "user" buộc mọi truy vấn thủ công phải trích
-- dẫn kép.
-- ==========================================================================================
CREATE TABLE app_user (
    id            UUID        PRIMARY KEY,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    enabled       BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_user IS 'Tài khoản đăng nhập. Tên bảng tránh từ khoá SQL "user".';
COMMENT ON COLUMN app_user.password_hash IS 'BCrypt. Không bao giờ ra khỏi service layer.';

-- UNIQUE trên lower(email), không phải trên email: 'A@x.com' và 'a@x.com' là cùng một người
-- trong nhận thức của người dùng. UNIQUE thường sẽ cho đăng ký trùng.
CREATE UNIQUE INDEX ux_app_user_email_lower ON app_user (lower(email));

-- ==========================================================================================
-- refresh_token — opaque token quay vòng, lưu hash (không phải chuỗi gốc, không phải BCrypt).
-- ==========================================================================================
CREATE TABLE refresh_token (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- SHA-256 hex của token gốc. Token là 256 bit ngẫu nhiên nên brute-force bất khả thi —
    -- không cần hàm băm chậm như BCrypt, và BCrypt có salt ngẫu nhiên nên không tra cứu được
    -- theo giá trị (sẽ phải quét toàn bảng).
    token_hash     TEXT        NOT NULL,
    -- Một "họ" = một chuỗi xoay vòng bắt đầu từ một lần đăng nhập. Phát hiện tái sử dụng thu
    -- hồi cả họ, không chỉ token bị lộ.
    family_id      UUID        NOT NULL,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoked_reason TEXT CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'EXPIRED')),
    user_agent     TEXT,

    UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_revoke_has_reason
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

COMMENT ON TABLE refresh_token IS 'Refresh token quay vòng. Tái sử dụng một token đã xoay thu hồi cả family_id.';

CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_user_active ON refresh_token (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_token_expiry ON refresh_token (expires_at) WHERE revoked_at IS NULL;

-- ==========================================================================================
-- project.owner_id — chủ sở hữu dự án, điểm cắm authorization.
--
-- Nullable ở V3 có chủ đích: chưa có Spring Security/JWT ở migration này nên ProjectService
-- chưa có cách nào biết "user hiện tại" để gán owner. Backfill dữ liệu cũ + SET NOT NULL để lại
-- cho V4, đúng lúc auth đã tồn tại và ProjectService bắt buộc nhận ownerId ở mọi lối vào — xem
-- V4__project_owner_not_null.sql.
-- ==========================================================================================
ALTER TABLE project ADD COLUMN owner_id UUID REFERENCES app_user (id) ON DELETE RESTRICT;

CREATE INDEX idx_project_owner ON project (owner_id, created_at DESC);

-- ==========================================================================================
-- repository_snapshot — trạng thái + nguồn, để upload có thể báo lỗi tường minh.
-- ==========================================================================================
ALTER TABLE repository_snapshot
    ADD COLUMN status TEXT NOT NULL DEFAULT 'READY'
        CHECK (status IN ('PENDING', 'EXTRACTING', 'READY', 'FAILED')),
    ADD COLUMN source_type TEXT NOT NULL DEFAULT 'ZIP_UPLOAD'
        CHECK (source_type IN ('ZIP_UPLOAD', 'GIT_CLONE')),
    ADD COLUMN file_count INT NOT NULL DEFAULT 0,
    ADD COLUMN total_size_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN error_message TEXT,
    ADD CONSTRAINT ck_snapshot_failed_has_reason
        CHECK (status <> 'FAILED' OR error_message IS NOT NULL);

-- DEFAULT 'READY' ở trên là để backfill hàng CŨ (coi như xong). Hàng MỚI phải bắt đầu ở PENDING.
ALTER TABLE repository_snapshot ALTER COLUMN status SET DEFAULT 'PENDING';

COMMENT ON COLUMN repository_snapshot.storage_key IS
    'Tiền tố (prefix) của snapshot trên object storage: snapshots/{id}/. Từ V3 mỗi file là một object riêng.';
COMMENT ON COLUMN repository_snapshot.source_type IS
    'Điểm mở rộng: GIT_CLONE chưa hiện thực ở tuần 5-7, cột có sẵn để tuần sau không phải migration lại.';

-- ==========================================================================================
-- snapshot_file — chỉ mục file của một snapshot, dựng cây file phẳng phía FE.
-- ==========================================================================================
CREATE TABLE snapshot_file (
    id           UUID        PRIMARY KEY,
    snapshot_id  UUID        NOT NULL REFERENCES repository_snapshot (id) ON DELETE CASCADE,
    -- Đường dẫn POSIX tương đối đã chuẩn hoá, không dấu '/' đầu, không segment '..'.
    path         TEXT        NOT NULL,
    -- Thư mục cha, '' cho gốc. Vật chất hoá để liệt kê một mức bằng một truy vấn có index.
    parent_path  TEXT        NOT NULL,
    name         TEXT        NOT NULL,
    entry_type   TEXT        NOT NULL CHECK (entry_type IN ('FILE', 'DIRECTORY')),
    extension    TEXT,
    size_bytes   BIGINT      NOT NULL DEFAULT 0,
    -- NULL cho thư mục và cho file nhị phân — "không đếm được", khác với "0 dòng".
    line_count   INT,
    sha256       TEXT,
    is_binary    BOOLEAN     NOT NULL DEFAULT false,
    -- false khi file vượt ngưỡng lưu trữ: vẫn hiện trong cây (cây phải trung thực) nhưng không
    -- có object để đọc.
    stored       BOOLEAN     NOT NULL DEFAULT true,
    storage_key  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (snapshot_id, path),

    -- Zip-slip ép ở tầng DB, không chỉ tin code Java. Regex thay vì position('..' in path) vì
    -- tên file hợp lệ có thể chứa '..' (vd 'a..b.txt') — chỉ segment '..' mới là tấn công.
    CONSTRAINT ck_snapshot_file_path_safe
        CHECK (path <> '' AND path !~ '^/' AND path !~ '(^|/)\.\.(/|$)' AND path !~ '\\'),
    CONSTRAINT ck_snapshot_file_binary_no_lines
        CHECK (NOT is_binary OR line_count IS NULL),
    CONSTRAINT ck_snapshot_file_stored_has_object
        CHECK (entry_type = 'DIRECTORY' OR NOT stored OR (storage_key IS NOT NULL AND sha256 IS NOT NULL))
);

COMMENT ON TABLE snapshot_file IS 'Chỉ mục phẳng của mọi file/thư mục trong một snapshot đã giải nén.';

-- Truy vấn chủ đạo: "liệt kê nội dung thư mục X", thư mục trước file, rồi theo tên.
CREATE INDEX idx_snapshot_file_dir ON snapshot_file (snapshot_id, parent_path, entry_type, name);
