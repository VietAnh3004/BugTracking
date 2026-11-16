package vn.hust.datn.evidence.service.account;

/**
 * Những gì lấy được từ một Google ID token ĐÃ kiểm chữ ký.
 *
 * @param subject claim {@code sub} — định danh bất biến của tài khoản Google
 * @param emailVerified Google xác nhận email này thuộc về chủ tài khoản. {@code false} thì email
 *     KHÔNG được dùng để liên kết — ai cũng tạo được tài khoản Google gắn một email chưa xác thực.
 * @param name tên hiển thị; có thể {@code null}
 * @param pictureUrl ảnh đại diện Google; có thể {@code null}
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name, String pictureUrl) {}
