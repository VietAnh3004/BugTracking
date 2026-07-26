package vn.hust.datn.evidence.config.properties;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Object storage (S3-compatible) lưu mã nguồn được upload.
 *
 * <p>Dùng object storage thay vì thư mục trên đĩa để backend không bị buộc phải nằm cùng máy với
 * các service khác, và để nhiều instance backend cùng đọc được một snapshot.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank String endpoint, @NotBlank String accessKey, @NotBlank String secretKey, @NotBlank String bucket) {}
