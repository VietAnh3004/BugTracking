package vn.hust.datn.evidence.exception;

/**
 * Yêu cầu thêm thành viên không thực hiện được — email chưa có tài khoản, hoặc mời chính chủ dự
 * án. 400 kèm lý do đọc được: người gọi là chủ dự án đang thao tác hợp lệ, cái họ cần là biết
 * phải làm gì tiếp.
 */
public class InvalidMemberException extends RuntimeException {

    public InvalidMemberException(String message) {
        super(message);
    }
}
