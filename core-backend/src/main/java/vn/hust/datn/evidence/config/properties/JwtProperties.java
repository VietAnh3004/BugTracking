package vn.hust.datn.evidence.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình JWT.
 *
 * <p>Hệ thống dùng bearer token stateless thay vì session cookie: FE và BE nằm trên hai domain
 * khác nhau, mà cookie cross-domain kéo theo {@code SameSite=None; Secure}, bắt buộc HTTPS và
 * cấu hình domain cha. Token trong header {@code Authorization} không có ràng buộc đó và cho phép
 * backend scale ngang không cần sticky session.
 *
 * @param secret khoá ký, tối thiểu 32 ký tự (HS256 cần khoá ≥ 256 bit)
 * @param expiration thời gian sống của access token
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET không được để trống") @Size(min = 32, message = "JWT_SECRET phải dài tối thiểu 32 ký tự") String secret,
        @NotNull Duration expiration) {

    /**
     * Bean Validation không có ràng buộc sẵn cho {@link Duration} ({@code @Positive} chỉ áp dụng
     * cho kiểu số), nên kiểm tra ở đây. Token có thời hạn bằng 0 hoặc âm sẽ hết hạn ngay khi vừa
     * cấp, khiến người dùng đăng nhập xong lại bị đẩy ra ngay.
     */
    public JwtProperties {
        if (expiration != null && (expiration.isZero() || expiration.isNegative())) {
            throw new IllegalArgumentException("JWT_EXPIRATION phải là khoảng thời gian dương, nhận được: " + expiration);
        }
    }
}
