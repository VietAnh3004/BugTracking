package vn.hust.datn.evidence.exception;

/**
 * Khoảng dòng yêu cầu không hợp lệ ({@code toLine < fromLine}, hoặc rộng hơn ngưỡng cho phép).
 * 400 Bad Request.
 *
 * <p>Không diễn đạt được bằng Bean Validation trên từng tham số: điều kiện là quan hệ GIỮA
 * {@code fromLine} và {@code toLine}, không phải ràng buộc của riêng tham số nào.
 */
public class InvalidLineRangeException extends RuntimeException {

    public InvalidLineRangeException(String message) {
        super(message);
    }
}
