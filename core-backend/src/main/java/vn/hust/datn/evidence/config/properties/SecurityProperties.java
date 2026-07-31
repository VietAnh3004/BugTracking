package vn.hust.datn.evidence.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình bảo mật ngoài JWT (xem {@link JwtProperties}).
 *
 * @param serviceToken bí mật dùng chung cho service-to-service (agent-service gọi
 *     {@code /api/v1/tools/**}), so khớp bằng {@code MessageDigest.isEqual} thời gian hằng — xem
 *     ADR 0005 điểm 2 (agent-service không forward JWT người dùng)
 * @param bcryptStrength log-rounds của BCrypt; mặc định 10 quá chậm cho bộ test (giây/lần hash),
 *     hạ xuống 4 trong {@code application-test.yml}
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotBlank(message = "SERVICE_TOKEN không được để trống") @Size(min = 32, message = "SERVICE_TOKEN phải dài tối thiểu 32 ký tự") String serviceToken,
        @Min(4) @Max(31) int bcryptStrength) {}
