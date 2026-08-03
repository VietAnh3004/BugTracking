package vn.hust.datn.evidence.config.properties;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Giới hạn chống zip-bomb khi giải nén snapshot upload — độc lập với {@code MAX_UPLOAD_SIZE} (giới
 * hạn byte thô của multipart request, ép ở tầng Tomcat/Spring trước khi zip được mở ra).
 *
 * <p>{@code maxCompressionRatio} so {@code tổng byte đã giải nén / byte zip gốc} — zip toàn số 0
 * có thể nén hàng nghìn lần, một file 10MB có thể "nở" thành hàng chục GB nếu không chặn theo tỷ
 * lệ, chỉ chặn theo tổng byte tuyệt đối thì vẫn phải đọc hết mới biết dừng.
 */
@Validated
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        @Min(1) int maxFiles,
        @Min(1) long maxUncompressedBytes,
        @Min(1) int maxCompressionRatio,
        @Min(1) long maxStoredFileBytes) {}
