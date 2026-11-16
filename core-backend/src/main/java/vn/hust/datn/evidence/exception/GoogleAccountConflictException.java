package vn.hust.datn.evidence.exception;

/** Email đã gắn với MỘT tài khoản Google khác. 409 — không ghi đè liên kết cũ. */
public class GoogleAccountConflictException extends RuntimeException {

    public GoogleAccountConflictException() {
        super("Email này đã được liên kết với một tài khoản Google khác.");
    }
}
