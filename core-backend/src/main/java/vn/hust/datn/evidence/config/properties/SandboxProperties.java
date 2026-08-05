package vn.hust.datn.evidence.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình chạy analyzer trong Docker sandbox (ADR 0008).
 *
 * <p><b>{@code runner} mặc định {@code disabled}.</b> Deploy lên môi trường không có Docker daemon
 * thì ứng dụng vẫn khởi động bình thường và mọi thứ khác vẫn chạy — đường nạp artifact thủ công
 * không phụ thuộc gì vào sandbox. Bật lên bằng {@code ANALYSIS_RUNNER=docker}.
 *
 * <p>Ba giới hạn tài nguyên đều có mặt vì mỗi cái chặn một kiểu hỏng khác nhau, xem chú thích từng
 * trường. Chúng KHÔNG thay thế nhau.
 *
 * @param runner        {@code disabled} | {@code docker}
 * @param javaImage     tag image analyzer Java; digest thật của lần chạy được ghi vào
 *                      {@code analysis_run.analyzer_image_digest}
 * @param timeout       hàng rào thời gian tường cho MỖI pha
 * @param memoryBytes   {@code --memory}; luôn đặt {@code --memory-swap} bằng đúng giá trị này —
 *                      quên là swap không giới hạn và giới hạn bộ nhớ mất tác dụng
 * @param pidsLimit     chặn fork bomb. Timeout KHÔNG chặn được kiểu này vì máy chết trước khi
 *                      timeout kịp nổ
 */
@Validated
@ConfigurationProperties(prefix = "app.sandbox")
public record SandboxProperties(
        // Ép đúng hai giá trị: bean SandboxRunner được chọn bằng @ConditionalOnProperty theo giá
        // trị này, nên một chuỗi lạ (gõ nhầm 'dockerr') sẽ khiến KHÔNG bean nào được nạp và ứng
        // dụng chết với "no qualifying bean" — một thông điệp chẳng nói lên nguyên nhân.
        @NotBlank @Pattern(regexp = "disabled|docker") String runner,
        @NotBlank String javaImage,
        @NotBlank String semgrepImage,
        Duration timeout,
        @Min(1) long memoryBytes,
        @Min(1) int pidsLimit,
        @Min(1) long cpus) {

    public boolean dockerEnabled() {
        return "docker".equalsIgnoreCase(runner);
    }
}
