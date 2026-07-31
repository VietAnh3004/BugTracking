package vn.hust.datn.evidence.exception;

/** Đăng ký với email đã có tài khoản (so khớp không phân biệt hoa/thường). 409 Conflict. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Email %s đã được đăng ký".formatted(email));
    }
}
