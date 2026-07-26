package vn.hust.datn.evidence.config.properties;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Origin của frontend được phép gọi API.
 *
 * <p>Frontend và backend deploy độc lập trên hai domain khác nhau, nên CORS là bắt buộc chứ không
 * phải tuỳ chọn. Giá trị phải khớp chính xác scheme + host + port mà trình duyệt đang dùng —
 * {@code http://localhost:3000} và {@code http://127.0.0.1:3000} là hai origin khác nhau.
 *
 * @param allowedOrigins danh sách origin, cấu hình qua biến môi trường {@code CORS_ORIGINS}
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty(message = "CORS_ORIGINS không được để trống") List<String> allowedOrigins) {}
