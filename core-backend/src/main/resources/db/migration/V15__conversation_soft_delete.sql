-- ==========================================================================================
-- V15 — người dùng xoá phiên hỏi Agent = ẨN, không xoá dòng.
-- ==========================================================================================

-- Xoá cứng một phiên kéo theo message, claim, trích dẫn và llm_trace (CASCADE) — mà llm_trace là
-- nguồn số liệu thực nghiệm không tái tạo được. Nên "xoá" ở giao diện chỉ đánh dấu thời điểm xoá:
-- phiên biến mất khỏi mọi API của người dùng, còn dữ liệu vẫn nằm đó cho phép đo. Dữ liệu chỉ bị dọn
-- thật khi xoá dự án hoặc tài khoản (CASCADE sẵn có).
ALTER TABLE conversation ADD COLUMN deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN conversation.deleted_at IS
    'Người dùng đã xoá phiên này (ẩn khỏi giao diện); NULL = còn hiển thị. Dữ liệu vẫn được giữ để đo thực nghiệm.';
