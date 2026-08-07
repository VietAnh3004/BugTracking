package vn.hust.datn.evidence.service.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.config.properties.AgentServiceProperties;
import vn.hust.datn.evidence.exception.AgentUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * Gọi {@code POST /analyze} của agent-service và đọc luồng SSE trả về.
 *
 * <p>Đọc thủ công từng dòng thay vì dùng WebClient reactive: cả ứng dụng là Spring MVC servlet
 * (một luồng cho mỗi request), thêm cả stack reactive chỉ để phân tích một định dạng gồm hai
 * loại dòng là cái giá không đáng.
 *
 * <p>Phân tích SSE ở đây cố ý TỐI GIẢN — chỉ hiểu {@code event:} và {@code data:} một dòng, vì
 * đó là đúng thứ agent-service phát ra và cả hai đầu nằm trong cùng repo. Không cố hiện thực
 * đầy đủ đặc tả (multi-line data, {@code id:}, {@code retry:}): hiện thực một nửa đặc tả là cách
 * chắc chắn để một ngày nào đó nuốt mất một sự kiện mà không ai biết.
 */
@Slf4j
@Component
public class AgentClient {

    private static final String EVENT_PREFIX = "event: ";
    private static final String DATA_PREFIX = "data: ";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AgentClient(AgentServiceProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .requestFactory(http11RequestFactory(properties))
                .baseUrl(properties.baseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * GHIM HTTP/1.1. Đây là bản vá cho một lỗi hỏng-im-lặng, không phải tinh chỉnh.
     *
     * <p>Không có Apache/Jetty/Netty trên classpath nên Spring chọn {@code HttpClient} của JDK,
     * và client đó mặc định thử nâng cấp lên HTTP/2 bằng cơ chế {@code h2c}: nó gửi request kèm
     * {@code Upgrade: h2c} và {@code Transfer-Encoding: chunked} — <b>nhưng KHÔNG gửi thân
     * request</b>. Phía uvicorn (HTTP/1.1) nhận được một POST rỗng và trả 422 "Field required".
     *
     * <p>Không có lỗi nào ở phía Java: request "thành công" theo mọi nghĩa nó biết. Bắt được bằng
     * cách dựng một server nghe thử và in nguyên văn thứ đi qua dây.
     */
    private static org.springframework.http.client.ClientHttpRequestFactory http11RequestFactory(
            AgentServiceProperties properties) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        // Hàng rào thời gian cho CẢ lượt, lấy từ app.agent.timeout (mặc định 120s). Một lượt suy
        // luận đi qua nhiều vòng gọi model nên nó phải rộng — nhưng vô hạn thì một agent treo sẽ
        // giữ luôn một luồng của agentExecutor cho tới khi tắt ứng dụng.
        factory.setReadTimeout(properties.timeout());
        return factory;
    }

    /**
     * Chạy một lượt hỏi và đẩy từng sự kiện qua {@code onEvent} ngay khi nhận được.
     *
     * <p>Chặn luồng gọi cho tới khi agent-service đóng stream. Người gọi phải ở trên luồng nền
     * (xem {@link vn.hust.datn.evidence.service.ConversationService}), không phải luồng xử lý
     * request của servlet container.
     */
    public void analyze(AnalyzeRequest request, Consumer<AgentEvent> onEvent) {
        restClient
                .post()
                .uri("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .exchange(
                        (req, response) -> {
                            if (response.getStatusCode().isError()) {
                                // Kèm THÂN response, không chỉ mã trạng thái: 422 của FastAPI nói
                                // rõ trường nào sai, và bỏ nó đi biến một lỗi hợp đồng sửa được
                                // trong một phút thành một cuộc điều tra.
                                throw new AgentUnavailableException("agent-service tra ve %s: %s"
                                        .formatted(response.getStatusCode(), readBody(response)));
                            }
                            readStream(response.getBody(), onEvent);
                            return null;
                        },
                        false);
    }

    /** Thân response lỗi, cắt ngắn — đủ để biết trường nào sai, không đủ để làm ngập log. */
    private static String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try (java.io.InputStream body = response.getBody()) {
            String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            return text.length() > 500 ? text.substring(0, 500) + "…" : text;
        } catch (IOException e) {
            return "(khong doc duoc than response)";
        }
    }

    /**
     * Package-private để test được TRỰC TIẾP trên một luồng byte.
     *
     * <p>Phần này là chỗ dễ đánh rơi sự kiện nhất, và một sự kiện bị đánh rơi không có thông báo
     * lỗi nào — chỉ là một câu trả lời không bao giờ tới. Test qua HTTP thật sẽ không phủ nổi các
     * ca biên (khung cuối thiếu dòng trống, dòng lạ xen giữa).
     */
    void readStream(java.io.InputStream body, Consumer<AgentEvent> onEvent) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String eventName = null;
            String data = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(EVENT_PREFIX)) {
                    eventName = line.substring(EVENT_PREFIX.length());
                } else if (line.startsWith(DATA_PREFIX)) {
                    data = line.substring(DATA_PREFIX.length());
                } else if (line.isEmpty() && eventName != null && data != null) {
                    onEvent.accept(new AgentEvent(eventName, data));
                    eventName = null;
                    data = null;
                }
            }
            // Khung cuối không có dòng trống theo sau: đừng đánh rơi nó. Chính khung `answer` hay
            // rơi vào tình huống này, và đánh rơi nó nghĩa là người dùng thấy tiến trình chạy
            // xong mà không bao giờ có câu trả lời.
            if (eventName != null && data != null) {
                onEvent.accept(new AgentEvent(eventName, data));
            }
        } catch (IOException e) {
            throw new AgentUnavailableException("dut ket noi toi agent-service: " + e.getMessage());
        }
    }

    /** Đọc payload của một sự kiện thành kiểu cụ thể. */
    public <T> T parse(AgentEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.data(), type);
        } catch (IOException e) {
            throw new AgentUnavailableException("agent-service tra ve payload khong doc duoc: " + e.getMessage());
        }
    }

    /** Một khung SSE đã tách tên và payload. */
    public record AgentEvent(String name, String data) {
        public static final String STATUS = "status";
        public static final String TOOL_CALL = "tool_call";
        public static final String TOOL_RESULT = "tool_result";
        public static final String ANSWER = "answer";
        public static final String ERROR = "error";
    }

    /** Thân request của {@code POST /analyze}, snake_case đúng hợp đồng của agent-service. */
    public record AnalyzeRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("analysis_run_id") UUID analysisRunId,
            String question,
            @com.fasterxml.jackson.annotation.JsonProperty("focus_short_id") String focusShortId,
            List<Map<String, String>> history) {

        public static AnalyzeRequest of(UUID runId, String question, String focusShortId, List<Map<String, String>> history) {
            return new AnalyzeRequest(runId, question, focusShortId, history == null ? new ArrayList<>() : history);
        }
    }
}
