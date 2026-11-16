-- ==========================================================================================
-- V11 — trạng thái embedding của một lần phân tích (tìm bằng chứng theo nghĩa).
--
-- Chốt với người dùng 16/11/2026: sinh vector NGAY SAU mỗi lần phân tích, bằng Gemini
-- (gemini-embedding-001, 768 chiều, qua agent-service). Gemini lỗi (hết quota, mất mạng, chưa bật)
-- thì lần phân tích VẪN thành công và search_evidence lùi về so chữ — nhưng lý do phải được GHI
-- LẠI ở đây và hiện ra giao diện. Tìm theo nghĩa âm thầm không chạy là đúng kiểu hỏng-im-lặng mà
-- cả đồ án chống: Agent nhận "không tìm thấy" và kết luận INSUFFICIENT_EVIDENCE nhầm.
--
-- Bảng evidence_embedding (V7) giữ nguyên: cột `embedding` vẫn KHÔNG khai số chiều. Không dựng
-- index HNSW có chủ đích — mọi truy vấn đều lọc theo MỘT run (vulnshop ~124 bằng chứng), quét
-- chính xác trên vài trăm hàng nhanh hơn và ĐÚNG hơn: index gần đúng + lọc sau theo run có thể
-- trả thiếu kết quả mà không báo gì.
-- ==========================================================================================
ALTER TABLE analysis_run
    ADD COLUMN embedding_model TEXT,
    ADD COLUMN embedding_count INT NOT NULL DEFAULT 0,
    ADD COLUMN embedding_error TEXT;

ALTER TABLE analysis_run
    ADD CONSTRAINT ck_analysis_run_embedding_count_nonneg CHECK (embedding_count >= 0);

COMMENT ON COLUMN analysis_run.embedding_model IS
    'Mô hình đã sinh vector cho run này; NULL khi chưa sinh. Truy vấn tìm theo nghĩa dùng đúng mô hình này.';
COMMENT ON COLUMN analysis_run.embedding_count IS
    'Số bằng chứng đã có vector. 0 = tìm kiếm của run này chỉ so chữ.';
COMMENT ON COLUMN analysis_run.embedding_error IS
    'Lý do lần sinh vector gần nhất thất bại; NULL khi thành công hoặc chưa thử.';
