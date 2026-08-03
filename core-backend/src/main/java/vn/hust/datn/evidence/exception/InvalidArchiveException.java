package vn.hust.datn.evidence.exception;

/**
 * File tải lên không phải zip hợp lệ, hoặc chứa entry không an toàn (zip-slip: đường dẫn tuyệt
 * đối, segment {@code ..}, ổ đĩa Windows, hai entry trùng path...). Ánh xạ sang HTTP 400 — lỗi đầu
 * vào của client, không phải lỗi hệ thống.
 */
public class InvalidArchiveException extends RuntimeException {

    public InvalidArchiveException(String message) {
        super(message);
    }

    public InvalidArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
