-- ==========================================================================================
-- V10 — thành viên dự án (chia sẻ dự án cho người khác).
--
-- Vì sao có: đồ án cần một người gán nhãn THỨ HAI, gán mù và độc lập, để tính Cohen's kappa
-- (kế hoạch §5). Trước V10 mọi tài nguyên chỉ chủ dự án đọc được, nên người thứ hai không mở nổi
-- lần phân tích cần gán nhãn.
--
-- Chủ dự án KHÔNG có hàng ở đây — vẫn là project.owner_id như cũ. Bảng này chỉ chứa người được
-- mời. Tách như vậy để mọi quy tắc "chỉ chủ dự án" (xoá dự án, tải mã, chạy phân tích, xem
-- dashboard, quản lý thành viên) vẫn chỉ cần so project.owner_id, không phải thêm cột vai trò rồi
-- nhớ lọc theo nó ở từng truy vấn.
--
-- Quyền của thành viên (chốt với người dùng 16/11/2026): xem mã nguồn, cảnh báo, bằng chứng; hỏi
-- Agent; đánh giá (human review); gán nhãn ground truth — và nhãn đó LUÔN là
-- BLIND_SECOND_RATER, server không bao giờ trả cho họ nhãn của chủ dự án.
-- ==========================================================================================
CREATE TABLE project_member (
    project_id UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (project_id, user_id)
);

-- Danh sách dự án của một người = dự án họ sở hữu HOẶC là thành viên: tra theo user_id.
CREATE INDEX idx_project_member_user ON project_member (user_id);

COMMENT ON TABLE project_member IS
    'Người được chia sẻ dự án (không gồm chủ dự án). Quyền: xem, hỏi Agent, review, gán nhãn mù.';
