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
 * <p><b>Hai mốc thời gian, cố ý tách bạch</b> (sửa 23/11/2026). Trước đây chỉ có {@code timeout}
 * = 120 giây tính trên TỔNG cả lượt, và nó cắt ba lượt Gemini khoẻ mạnh nhưng chậm (máy chủ model
 * trả 429/503, agent tự thử lại) với thông báo vô nghĩa "dut ket noi ... closed".
 *
 * @param baseUrl URL nội bộ tới agent-service
 * @param timeout trần TỔNG của một lượt, chỉ để chặn treo vô hạn — đặt rộng
 * @param idleTimeout thời gian IM LẶNG tối đa: không nhận được dòng nào (kể cả nhịp {@code : ping}
 *     agent-service gửi mỗi 15 giây) trong khoảng này thì coi là treo và cắt
 */
@Validated
@ConfigurationProperties(prefix = "app.agent")
public record AgentServiceProperties(@NotBlank String baseUrl, Duration timeout, Duration idleTimeout) {

    public AgentServiceProperties {
        timeout = timeout == null ? Duration.ofMinutes(15) : timeout;
        idleTimeout = idleTimeout == null ? Duration.ofSeconds(90) : idleTimeout;
    }
}
