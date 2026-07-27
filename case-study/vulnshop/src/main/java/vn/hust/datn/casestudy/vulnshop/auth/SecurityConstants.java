package vn.hust.datn.casestudy.vulnshop.auth;

/** Hằng số dùng cho xác thực. */
public final class SecurityConstants {

    /**
     * SEEDED — lỗi thật: khoá bí mật nhúng thẳng trong mã nguồn.
     *
     * <p>Bất kỳ ai đọc được repository đều ký được token hợp lệ. Khoá phải đến từ biến môi trường.
     * Kỳ vọng Semgrep và find-sec-bugs cùng bắt (hard-coded-secret / HARD_CODE_PASSWORD).
     */
    public static final String JWT_SIGNING_KEY = "s3cr3t-signing-key-do-not-share-2026";

    /**
     * SEEDED — lỗi thật: mật khẩu quản trị mặc định trong mã nguồn.
     *
     * <p>Tài khoản này tồn tại trên mọi bản triển khai với cùng một mật khẩu.
     */
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    /** Số vòng lặp băm — hằng số cấu hình bình thường, không phải bí mật. */
    public static final int HASH_ITERATIONS = 1;

    private SecurityConstants() {}
}
