package vn.hust.datn.evidence.domain;

/**
 * Vai trò của NGƯỜI ĐANG GỌI đối với một dự án — không lưu ở đâu cả, suy ra từ
 * {@code project.owner_id} và bảng {@code project_member}.
 *
 * <p>Trả ra API để giao diện ẩn những nút thành viên không dùng được (xoá dự án, chạy phân tích…).
 * Ẩn nút chỉ là tiện dụng; backend vẫn kiểm lại quyền ở từng endpoint.
 */
public enum ProjectRole {
    /** Toàn quyền: quản lý thành viên, tải mã, chạy phân tích, dashboard, xuất báo cáo, xoá. */
    OWNER,
    /** Xem, hỏi Agent, review, gán nhãn ground truth với vai trò người gán thứ hai (mù). */
    MEMBER
}
