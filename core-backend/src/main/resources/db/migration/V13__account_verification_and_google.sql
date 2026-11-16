-- ==========================================================================================
-- V13 — tài khoản: xác thực email, quên mật khẩu, đăng nhập bằng Google.
-- ==========================================================================================

-- Tài khoản tạo bằng Google KHÔNG có mật khẩu. Ràng buộc bên dưới giữ bất biến "mỗi tài khoản
-- đăng nhập được bằng ít nhất một cách" — thiếu nó, một tài khoản vừa mất mật khẩu vừa không
-- có Google là tài khoản chết không ai vào được.
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;
-- "sub" trong ID token của Google: định danh BẤT BIẾN của tài khoản Google. Không dùng email để
-- nhận người dùng Google — email của một tài khoản Google đổi được, sub thì không.
ALTER TABLE app_user ADD COLUMN google_subject TEXT;
ALTER TABLE app_user ADD COLUMN google_picture_url TEXT;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_can_sign_in
        CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL);

CREATE UNIQUE INDEX ux_app_user_google_subject ON app_user (google_subject) WHERE google_subject IS NOT NULL;

-- Tài khoản có từ trước V13 coi như đã xác thực: chúng được tạo khi hệ thống chưa có bước này,
-- chặn chúng lại là khoá cửa chính chủ (kể cả tài khoản đang dùng cho user study).
UPDATE app_user SET email_verified_at = created_at;

COMMENT ON COLUMN app_user.email_verified_at IS
    'NULL = chưa xác thực email, chưa đăng nhập được bằng mật khẩu.';
COMMENT ON COLUMN app_user.google_subject IS 'Claim sub của Google ID token; NULL = chưa liên kết Google.';

-- ==========================================================================================
-- account_token — token dùng MỘT lần gửi qua email (xác thực email, đặt lại mật khẩu).
-- Lưu SHA-256 của token như refresh_token (V3), cùng lý do: token 256 bit ngẫu nhiên, cần tra
-- theo giá trị.
-- ==========================================================================================
CREATE TABLE account_token (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose     TEXT        NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash  TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_account_token_hash ON account_token (token_hash);
-- Tra "token gần nhất của người này cho mục đích này" — dùng để giãn cách gửi lại email.
CREATE INDEX ix_account_token_user_purpose ON account_token (user_id, purpose, created_at DESC);

-- Đổi/đặt lại mật khẩu thu hồi MỌI phiên đang mở: lý do riêng để nhật ký phân biệt được với đăng
-- xuất thường. Tên ràng buộc là tên Postgres tự sinh cho CHECK khai cạnh cột ở V3 — cố ý KHÔNG
-- dùng IF EXISTS: tên sai thì migration phải đổ ngay, không lặng lẽ bỏ qua.
ALTER TABLE refresh_token DROP CONSTRAINT refresh_token_revoked_reason_check;
ALTER TABLE refresh_token
    ADD CONSTRAINT refresh_token_revoked_reason_check
        CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'EXPIRED', 'PASSWORD_CHANGED'));
