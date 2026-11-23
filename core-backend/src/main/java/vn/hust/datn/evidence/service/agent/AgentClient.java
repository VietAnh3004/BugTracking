package vn.hust.datn.evidence.service.agent;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

    /** Dòng chú thích SSE (đặc tả: bắt đầu bằng {@code :}) — agent-service dùng làm nhịp giữ kết nối. */
    private static final String COMMENT_PREFIX = ":";

    /**
     * MỘT luồng nền dùng chung cho mọi lượt hỏi: việc của nó chỉ là nhìn đồng hồ, không đáng một luồng
     * mỗi lượt. Daemon để không giữ JVM khi tắt ứng dụng.
     */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-idle-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final Duration idleTimeout;

    public AgentClient(AgentServiceProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .requestFactory(http11RequestFactory(properties))
                .baseUrl(properties.baseUrl())
                .build();
        this.objectMapper = objectMapper;
        this.timeout = properties.timeout();
        this.idleTimeout = properties.idleTimeout();
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
        // Trần TỔNG cho cả lượt (app.agent.timeout, mặc định 15 phút): chỉ để một agent treo không
        // giữ mãi một luồng của agentExecutor. Mốc thường gặp là idle-timeout, do IdleWatchdog canh.
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
        analyze(request, onEvent, connection -> {});
    }

    /**
     * Như {@link #analyze(AnalyzeRequest, Consumer)}, kèm {@code onConnected} nhận về KẾT NỐI tới
     * agent-service ngay khi nó mở — để luồng khác đóng được nó khi người dùng huỷ lượt hỏi.
     *
     * <p>Đóng kết nối là cách huỷ DUY NHẤT có tác dụng tới tận agent-service: phía đó thấy client
     * ngắt và thôi gọi LLM (xem {@code stop_on_disconnect} trong agent-service). Luồng đang chặn ở
     * {@code readLine} sẽ nhận {@code IOException} và thoát ra dưới dạng
     * {@link AgentUnavailableException} — người gọi tự phân biệt huỷ có chủ đích với lỗi thật.
     */
    public void analyze(AnalyzeRequest request, Consumer<AgentEvent> onEvent, Consumer<Closeable> onConnected) {
        long startedAt = System.nanoTime();
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
                            // Đóng THÂN response, KHÔNG đóng `response`: JdkClientHttpResponse.close()
                            // đọc cạn (drain) phần còn lại trước khi đóng, tức là CHẶN cho tới khi
                            // agent-service tự chạy xong. Gặp thật 23/11/2026: watchdog lẫn nút Dừng
                            // đều kẹt trong close() suốt cả lượt, nên không cắt/huỷ được gì.
                            java.io.InputStream body = response.getBody();
                            onConnected.accept(body);
                            IdleWatchdog watchdog = new IdleWatchdog(body, idleTimeout);
                            try {
                                readStream(body, onEvent, line -> {
                                    watchdog.touch();
                                    if (line.startsWith(COMMENT_PREFIX)) {
                                        onEvent.accept(new AgentEvent(AgentEvent.HEARTBEAT, ""));
                                    }
                                });
                            } catch (AgentUnavailableException e) {
                                throw explainDisconnect(e, watchdog, startedAt);
                            } finally {
                                watchdog.stop();
                            }
                            return null;
                        },
                        false);
    }

    /**
     * Đổi "đứt kết nối" thành lý do người dùng hiểu được, khi biết chắc lý do.
     *
     * <p>Chỉ áp cho lỗi ĐỌC STREAM ({@code cause} là {@link IOException}); sự kiện {@code error} của
     * agent-service cũng thoát ra dưới dạng {@link AgentUnavailableException} nhưng mang thông điệp
     * riêng của nó, không được ghi đè.
     */
    private RuntimeException explainDisconnect(AgentUnavailableException e, IdleWatchdog watchdog, long startedAt) {
        if (!(e.getCause() instanceof IOException)) {
            return e;
        }
        if (watchdog.fired()) {
            return new AgentUnavailableException(
                    "Agent không phản hồi trong %s (máy chủ model có thể đang quá tải). Câu hỏi vẫn nằm trong lịch sử, bạn có thể hỏi lại."
                            .formatted(humanize(idleTimeout)),
                    e.getCause());
        }
        if (Duration.ofNanos(System.nanoTime() - startedAt).compareTo(timeout) >= 0) {
            return new AgentUnavailableException(
                    "Lượt hỏi vượt quá giới hạn %s nên đã bị dừng. Câu hỏi vẫn nằm trong lịch sử, bạn có thể hỏi lại."
                            .formatted(humanize(timeout)),
                    e.getCause());
        }
        return e;
    }

    private static String humanize(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds == 0) {
            return duration.toMillis() + " ms";
        }
        return seconds >= 60 && seconds % 60 == 0 ? (seconds / 60) + " phút" : seconds + " giây";
    }

    /**
     * Cắt lượt hỏi khi agent-service IM LẶNG quá {@code idleTimeout}.
     *
     * <p>Đóng THÂN response làm lần {@code read} đang chờ ở luồng đọc ném {@code IOException("closed")}
     * ngay (đo thật: ~50 ms sau mốc). Cờ {@link #fired()} để người gọi phân biệt "bị cắt vì im lặng"
     * với nút Dừng hay lỗi mạng.
     */
    static final class IdleWatchdog {
        private final Closeable body;
        private final long idleNanos;
        private final ScheduledFuture<?> task;
        private volatile long lastActivity = System.nanoTime();
        private volatile boolean fired;
        private volatile boolean stopped;

        IdleWatchdog(Closeable body, Duration idleTimeout) {
            this.body = body;
            this.idleNanos = idleTimeout.toNanos();
            // Kiểm thưa (≤ 1 giây) là đủ: sai số vài trăm mili giây trên mốc 90 giây không đáng kể.
            long periodMillis = Math.clamp(idleTimeout.toMillis() / 4, 10, 1000);
            this.task = WATCHDOG.scheduleWithFixedDelay(this::check, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        }

        void touch() {
            lastActivity = System.nanoTime();
        }

        boolean fired() {
            return fired;
        }

        void stop() {
            stopped = true;
            task.cancel(false);
        }

        private void check() {
            if (fired || stopped || System.nanoTime() - lastActivity < idleNanos) {
                return;
            }
            fired = true;
            try {
                body.close();
            } catch (IOException | RuntimeException ignored) {
                // Đóng hỏng thì luồng đọc vẫn thoát khi trần tổng (app.agent.timeout) tới.
            }
        }
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
        readStream(body, onEvent, line -> {});
    }

    /** Như trên, kèm {@code onLine} nhận MỌI dòng đọc được — kể cả dòng chú thích không thành khung. */
    void readStream(java.io.InputStream body, Consumer<AgentEvent> onEvent, Consumer<String> onLine) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String eventName = null;
            String data = null;
            String line;
            while ((line = reader.readLine()) != null) {
                onLine.accept(line);
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
            throw new AgentUnavailableException("dut ket noi toi agent-service: " + e.getMessage(), e);
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
        /** Nhịp giữ kết nối (dòng chú thích SSE), không có dữ liệu. Không lưu, chỉ chuyển tiếp. */
        public static final String HEARTBEAT = "heartbeat";
    }

    /** Thân request của {@code POST /analyze}, snake_case đúng hợp đồng của agent-service. */
    public record AnalyzeRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("analysis_run_id") UUID analysisRunId,
            String question,
            @com.fasterxml.jackson.annotation.JsonProperty("focus_short_id") String focusShortId,
            List<Map<String, String>> history,
            @com.fasterxml.jackson.annotation.JsonProperty("model_tier") String modelTier) {

        public static AnalyzeRequest of(UUID runId, String question, String focusShortId, List<Map<String, String>> history) {
            return of(runId, question, focusShortId, history, vn.hust.datn.evidence.domain.AgentModel.FREE);
        }

        public static AnalyzeRequest of(
                UUID runId,
                String question,
                String focusShortId,
                List<Map<String, String>> history,
                vn.hust.datn.evidence.domain.AgentModel model) {
            return new AnalyzeRequest(
                    runId, question, focusShortId, history == null ? new ArrayList<>() : history, model.wireValue());
        }
    }
}
