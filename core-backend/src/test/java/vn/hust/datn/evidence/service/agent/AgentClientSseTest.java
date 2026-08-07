package vn.hust.datn.evidence.service.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.config.properties.AgentServiceProperties;

/**
 * Phân tích khung SSE — thuần hàm, không Spring, không mạng.
 *
 * <p>Đây là chỗ dễ đánh rơi sự kiện nhất trong cả luồng, và một sự kiện bị đánh rơi KHÔNG có
 * thông báo lỗi nào: người dùng chỉ thấy tiến trình chạy xong mà câu trả lời không bao giờ tới.
 */
class AgentClientSseTest {

    private final AgentClient client =
            new AgentClient(new AgentServiceProperties("http://agent:8000", java.time.Duration.ofSeconds(120)), new ObjectMapper());

    private List<AgentClient.AgentEvent> parse(String body) {
        List<AgentClient.AgentEvent> events = new ArrayList<>();
        client.readStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), events::add);
        return events;
    }

    @Test
    void docDuocNhieuKhungLienTiep() {
        List<AgentClient.AgentEvent> events = parse(
                """
                event: status
                data: {"iteration":0}

                event: tool_call
                data: {"name":"get_warning"}

                """);

        assertThat(events).extracting(AgentClient.AgentEvent::name).containsExactly("status", "tool_call");
        assertThat(events.getFirst().data()).isEqualTo("{\"iteration\":0}");
    }

    /**
     * Khung CUỐI thường không có dòng trống theo sau, và khung cuối thường chính là {@code answer}.
     * Đánh rơi nó nghĩa là mọi lượt hỏi đáp đều kết thúc mà không có câu trả lời.
     */
    @Test
    void khungCuoiThieuDongTrongVanDuocDoc() {
        List<AgentClient.AgentEvent> events = parse("event: answer\ndata: {\"answer\":{}}");

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().name()).isEqualTo("answer");
    }

    /** Dòng không thuộc hai tiền tố đã biết (comment giữ nhịp của SSE) không được sinh khung. */
    @Test
    void dongLaKhongSinhKhung() {
        List<AgentClient.AgentEvent> events = parse(
                """
                : giu nhip
                event: status
                data: {"iteration":0}

                """);

        assertThat(events).hasSize(1);
    }

    /** Luồng rỗng (agent-service đóng ngay) không được sinh khung ma. */
    @Test
    void luongRongKhongSinhKhungNao() {
        assertThat(parse("")).isEmpty();
    }

    /** Khung thiếu {@code data:} là khung dở dang — bỏ qua thay vì đẩy null xuống phía sau. */
    @Test
    void khungThieuDataBiBoQua() {
        assertThat(parse("event: status\n\n")).isEmpty();
    }
}
