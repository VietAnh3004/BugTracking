package vn.hust.datn.evidence.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tài khoản quản trị DUY NHẤT, khai bằng biến môi trường — xem {@code AdminBootstrap}.
 *
 * <p>Cả hai để trống = không có admin (mặc định): trang quản trị không dùng được, nhưng ứng dụng
 * vẫn khởi động bình thường. Khai MỘT trong hai là cấu hình dở dang, nên nổ ngay lúc khởi động
 * ({@code @ConfigurationProperties} không tự fail-fast khi thiếu biến, xem CLAUDE.md) thay vì để
 * người vận hành tưởng đã có admin.
 *
 * <p>{@code toString} bị ghi đè để mật khẩu KHÔNG bao giờ lọt vào log hay thông điệp lỗi — mặc định
 * của record in ra mọi thành phần.
 *
 * @param username tên đăng nhập admin; lưu ở cột {@code email} của {@code app_user}. KHÔNG cần là
 *     địa chỉ email (đăng nhập không kiểm định dạng), nên không trùng được với tài khoản đăng ký
 *     và không dùng được "quên mật khẩu"
 * @param password mật khẩu, tối thiểu 8 ký tự
 */
@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String password) {

    private static final int MIN_PASSWORD_LENGTH = 8;

    public AdminProperties {
        username = username == null ? "" : username.strip();
        password = password == null ? "" : password;
        if (username.isEmpty() != password.isEmpty()) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME và ADMIN_PASSWORD phải khai CÙNG LÚC (hoặc cùng để trống để tắt admin)");
        }
        if (!password.isEmpty() && password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD phải dài tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự");
        }
    }

    public boolean enabled() {
        return !username.isEmpty();
    }

    @Override
    public String toString() {
        return "AdminProperties[enabled=" + enabled() + "]";
    }
}
