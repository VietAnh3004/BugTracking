package vn.hust.datn.evidence.exception;

/** Mật khẩu đúng nhưng email chưa xác thực. 403 — chỉ lộ ra SAU khi mật khẩu đúng, nên không dùng để dò tài khoản được. */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email chưa được xác thực. Hãy mở link trong email xác thực, hoặc bấm Gửi lại.");
    }
}
