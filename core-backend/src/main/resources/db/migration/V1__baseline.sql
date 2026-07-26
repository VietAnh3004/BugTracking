-- Baseline schema.
--
-- Unified Evidence Model được thiết kế ở tuần 3-4 và sẽ vào V2; ở đây chỉ tạo hai bảng
-- đã chắc chắn về hình dạng, đủ để xác nhận đường ống Flyway hoạt động.
--
-- Extension (vector, pg_trgm) do infra/init/01-extensions.sql tạo vì cần quyền superuser.

CREATE TABLE project (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE project IS 'Một dự án được phân tích; gom các phiên bản mã nguồn và phiên phân tích.';

-- Một lần nộp mã nguồn để phân tích. Bằng chứng luôn gắn với một snapshot cụ thể:
-- cùng một cảnh báo ở hai commit khác nhau là hai bằng chứng khác nhau, vì vị trí
-- dòng lệnh có thể đã dịch chuyển.
CREATE TABLE repository_snapshot (
    id           UUID PRIMARY KEY,
    project_id   UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    commit_sha   TEXT,
    source_ref   TEXT        NOT NULL,
    storage_key  TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN repository_snapshot.source_ref IS 'Nhánh, tag hoặc tên file zip đã upload.';
COMMENT ON COLUMN repository_snapshot.storage_key IS 'Khoá của object trong S3-compatible storage.';

CREATE INDEX idx_repository_snapshot_project ON repository_snapshot (project_id, created_at DESC);
