package vn.hust.datn.evidence.exception;

/** Đổi mật khẩu mà mật khẩu hiện tại sai. 400 chứ KHÔNG 401: frontend coi 401 là hết phiên và tự đi refresh token. */
public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("Mật khẩu hiện tại không đúng.");
    }
}
