package vn.hust.datn.evidence.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tài khoản: xác thực email, quên mật khẩu, đăng nhập Google.
 *
 * <p>SMTP KHÔNG nằm ở đây mà ở {@code spring.mail.*} của Spring Boot — chỉ những gì riêng của
 * ứng dụng mới ở {@code app.account}.
 *
 * @param publicUrl URL frontend mà NGƯỜI DÙNG mở (không phải hostname nội bộ docker) — link
 *     trong email trỏ về đây. Khác {@code CORS_ORIGINS}: đó là danh sách, đây là MỘT địa chỉ.
 * @param verifyEmailTtl hạn của link xác thực email
 * @param resetPasswordTtl hạn của link đặt lại mật khẩu — ngắn hơn hẳn vì nó trao quyền vào tài
 *     khoản
 * @param resendCooldown khoảng cách tối thiểu giữa hai email cùng loại cho một người — chặn dùng
 *     form "quên mật khẩu" để dội email vào hộp thư người khác
 * @param mailFrom địa chỉ người gửi; rỗng = dùng {@code spring.mail.username} (Gmail bắt buộc
 *     người gửi trùng tài khoản đăng nhập SMTP). Nhận cả dạng {@code Tên <địa-chỉ>}.
 * @param mailFromName tên người gửi hiện trong hộp thư (vd "Evidence Agent"); rỗng = chỉ hiện địa
 *     chỉ. Đặt tên ở đây thay vì nhét vào {@code mailFrom} để tên có dấu được mã hoá đúng.
 * @param mailAsync gửi email trên luồng riêng sau khi commit. Tắt trong test để kiểm nội dung
 *     email ngay sau request.
 * @param googleClientId OAuth Client ID của Google; rỗng = tắt đăng nhập Google
 */
@Validated
@ConfigurationProperties(prefix = "app.account")
public record AccountProperties(
        @NotBlank String publicUrl,
        @NotNull Duration verifyEmailTtl,
        @NotNull Duration resetPasswordTtl,
        @NotNull Duration resendCooldown,
        String mailFrom,
        String mailFromName,
        boolean mailAsync,
        String googleClientId) {

    public boolean googleEnabled() {
        return googleClientId != null && !googleClientId.isBlank();
    }

    /** {@code publicUrl} không kèm dấu {@code /} cuối, để ghép đường dẫn không ra {@code //}. */
    public String publicUrlWithoutTrailingSlash() {
        return publicUrl.replaceAll("/+$", "");
    }

    /**
     * Tên thương hiệu hiện trên đầu và chân email: {@code MAIL_FROM_NAME} (cùng tên ở ô "Từ" của hộp
     * thư — hai chỗ khác tên trông như thư giả mạo), trống thì là tên miền của {@code publicUrl}.
     */
    public String brandName() {
        if (mailFromName != null && !mailFromName.isBlank()) {
            return mailFromName.trim();
        }
        String host = java.net.URI.create(publicUrlWithoutTrailingSlash()).getHost();
        return host == null ? publicUrl : host;
    }
}
