package vn.hust.datn.evidence.exception;

/** Google ID token không qua được kiểm tra, hoặc Google chưa xác thực email đó. 401. */
public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException() {
        super("Không xác thực được tài khoản Google. Hãy thử lại.");
    }
}
