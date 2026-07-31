package vn.hust.datn.evidence.config.properties;

import java.nio.charset.StandardCharsets;
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
 * @param secret khoá ký HS256, tối thiểu 32 ký tự
 * @param expiration thời gian sống của access token — ngắn (mặc định 15 phút) vì có refresh token
 *     quay vòng bù lại; access token không thu hồi được nên phải tự hết hạn nhanh
 * @param refreshExpiration thời gian sống của refresh token (opaque, lưu hash trong DB, thu hồi
 *     được) — dài hơn nhiều vì đây là thứ giữ phiên đăng nhập sống qua việc reload trang
 * @param issuer giá trị claim {@code iss}, validate khi giải mã token
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET không được để trống") @Size(min = 32, message = "JWT_SECRET phải dài tối thiểu 32 ký tự") String secret,
        @NotNull Duration expiration,
        @NotNull Duration refreshExpiration,
        @NotBlank(message = "JWT_ISSUER không được để trống") String issuer) {

    /**
     * Bean Validation không có ràng buộc sẵn cho {@link Duration} ({@code @Positive} chỉ áp dụng
     * cho kiểu số), nên kiểm tra ở đây. Token có thời hạn bằng 0 hoặc âm sẽ hết hạn ngay khi vừa
     * cấp, khiến người dùng đăng nhập xong lại bị đẩy ra ngay.
     *
     * <p>Kiểm thêm độ dài theo BYTE UTF-8 (không chỉ dựa vào {@code @Size(min = 32)} đếm ký tự)
     * để thông điệp lỗi nói đúng ngôn ngữ của HS256 ("cần 256 bit/32 byte") thay vì nói về ký tự —
     * dễ hiểu hơn khi ai đó cấu hình sai và Nimbus sẽ ném {@code KeyLengthException} nếu lọt qua
     * được tới đây. (Với UTF-8, một chuỗi đủ 32 ký tự luôn có ≥ 32 byte, nên nhánh này không bao
     * giờ bắt được lỗi mà {@code @Size} bỏ sót — nó chỉ đổi thông điệp cho đúng ngữ cảnh bảo mật.)
     */
    public JwtProperties {
        if (expiration != null && (expiration.isZero() || expiration.isNegative())) {
            throw new IllegalArgumentException("JWT_EXPIRATION phải là khoảng thời gian dương, nhận được: " + expiration);
        }
        if (refreshExpiration != null && (refreshExpiration.isZero() || refreshExpiration.isNegative())) {
            throw new IllegalArgumentException(
                    "JWT_REFRESH_EXPIRATION phải là khoảng thời gian dương, nhận được: " + refreshExpiration);
        }
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT_SECRET phải có tối thiểu 32 byte khi mã hoá UTF-8 (HS256 cần khoá >= 256 bit), "
                            + "hiện có " + secret.getBytes(StandardCharsets.UTF_8).length + " byte");
        }
    }
}
