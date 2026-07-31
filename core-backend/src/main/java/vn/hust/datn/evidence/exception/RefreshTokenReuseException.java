package vn.hust.datn.evidence.exception;

/**
 * Một refresh token đã bị xoay (hoặc đã thu hồi) nhưng lại được dùng lần nữa — dấu hiệu token đã
 * bị lộ, hoặc client hỏng. Cả họ token (cùng {@code family_id}) bị thu hồi khi ngoại lệ này ném
 * ra. 401 Unauthorized.
 */
public class RefreshTokenReuseException extends RuntimeException {

    public RefreshTokenReuseException() {
        super("Refresh token đã xoay vòng nhưng bị dùng lại - toàn bộ phiên đã bị thu hồi");
    }
}
