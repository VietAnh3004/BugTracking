package vn.hust.datn.evidence.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Địa chỉ agent-service.
 *
 * <p>Agent chạy trong process riêng và không truy cập cơ sở dữ liệu trực tiếp — nó gọi ngược lại
 * Evidence Tool API của service này. Nhờ vậy mỗi lượt truy xuất bằng chứng của agent đều đi qua
 * một ranh giới quan sát được, phục vụ đo đạc trong thực nghiệm.
 *
 * @param baseUrl URL nội bộ tới agent-service
 * @param timeout thời gian chờ tối đa cho một lượt suy luận (agent gọi LLM nhiều vòng nên lâu)
 */
@Validated
@ConfigurationProperties(prefix = "app.agent")
public record AgentServiceProperties(@NotBlank String baseUrl, Duration timeout) {}
