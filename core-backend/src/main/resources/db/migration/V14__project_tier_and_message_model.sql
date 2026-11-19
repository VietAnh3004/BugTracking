-- ==========================================================================================
-- V14 — hạng dự án (Free/Premium) và model đã trả lời từng lượt.
-- ==========================================================================================

-- Hạng gắn với DỰ ÁN chứ không với người: quyền dùng model Premium đi theo dự án, nên chủ một
-- dự án Premium mở dự án khác (Free) thì ở dự án đó vẫn chỉ có model Free. Chỉ admin đổi được.
ALTER TABLE project
    ADD COLUMN tier TEXT NOT NULL DEFAULT 'FREE'
        CONSTRAINT ck_project_tier CHECK (tier IN ('FREE', 'PREMIUM'));

COMMENT ON COLUMN project.tier IS
    'FREE: hội thoại chỉ dùng model miễn phí. PREMIUM: người dùng được chọn model Premium từng câu hỏi.';

-- Model đã sinh câu trả lời (lấy từ llm_trace). Lưu ngay trên message để giao diện ghi nhãn
-- "trả lời bởi ..." mà không phải join; NULL với lượt của người dùng và với dữ liệu cũ.
ALTER TABLE message ADD COLUMN model TEXT;

COMMENT ON COLUMN message.model IS 'Model đã trả lời lượt này; NULL với câu hỏi của người dùng và dữ liệu trước V14.';
