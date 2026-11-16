package vn.hust.datn.evidence.exception;

/** Link xác thực email / đặt lại mật khẩu sai, hết hạn hoặc đã dùng. 400 — gộp chung ba trường hợp: phân biệt chúng chỉ giúp kẻ đoán token. */
public class InvalidAccountTokenException extends RuntimeException {

    public InvalidAccountTokenException() {
        super("Liên kết không hợp lệ hoặc đã hết hạn. Hãy yêu cầu gửi lại email.");
    }
}
