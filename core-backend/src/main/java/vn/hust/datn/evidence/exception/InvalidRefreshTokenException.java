package vn.hust.datn.evidence.exception;

/** Refresh token không tồn tại hoặc đã hết hạn. 401 Unauthorized. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String reason) {
        super("Refresh token không hợp lệ: " + reason);
    }
}
