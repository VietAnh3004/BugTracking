package vn.hust.datn.evidence.exception;

/**
 * Đăng nhập sai. Dùng CHUNG một thông điệp cho "email không tồn tại" và "sai mật khẩu" — chống
 * dò tài khoản qua thông điệp lỗi khác nhau. 401 Unauthorized.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email hoặc mật khẩu không đúng");
    }
}
