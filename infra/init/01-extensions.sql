-- Chạy một lần khi container postgres khởi tạo volume lần đầu.
-- Schema của ứng dụng do Flyway quản lý (core-backend/src/main/resources/db/migration),
-- ở đây chỉ bật extension vì tạo extension cần quyền superuser.

CREATE EXTENSION IF NOT EXISTS vector;      -- pgvector: embedding cho semantic retrieval
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- trigram: tìm kiếm lexical trên nội dung evidence
