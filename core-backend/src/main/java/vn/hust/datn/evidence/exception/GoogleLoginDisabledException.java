package vn.hust.datn.evidence.exception;

/** Máy chủ chưa cấu hình GOOGLE_CLIENT_ID. 503. */
public class GoogleLoginDisabledException extends RuntimeException {

    public GoogleLoginDisabledException() {
        super("Đăng nhập bằng Google chưa được bật trên máy chủ này.");
    }
}
