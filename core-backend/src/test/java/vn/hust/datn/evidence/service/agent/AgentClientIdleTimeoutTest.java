package vn.hust.datn.evidence.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import vn.hust.datn.evidence.config.properties.AgentServiceProperties;
import vn.hust.datn.evidence.exception.AgentUnavailableException;

/**
 * Timeout tính theo thời gian IM LẶNG, qua HTTP thật (server nghe thử của JDK).
 *
 * <p>Lỗi gặp thật 20/11/2026: timeout cũ 120 giây tính trên TỔNG cả lượt, nên ba lượt Gemini khoẻ
 * mạnh nhưng chậm (máy chủ model trả 429/503, agent thử lại) bị cắt đúng 120 giây với thông báo
 * "dut ket noi toi agent-service: closed". Test dùng mốc mili giây để chạy nhanh; cơ chế y hệt.
 */
class AgentClientIdleTimeoutTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Dựng agent-service giả: {@code script} ghi từng mẩu, ngủ giữa chừng nếu cần. */
    private AgentClient clientFor(Script script, Duration idleTimeout) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/analyze", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                script.run(chunk -> {
                    out.write(chunk.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                });
            } catch (IOException | InterruptedException ignored) {
                // Client đã cắt kết nối: đúng thứ một số test chờ đợi.
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AgentClient(
                new AgentServiceProperties(baseUrl, Duration.ofSeconds(30), idleTimeout), new ObjectMapper());
    }

    private static AgentClient.AnalyzeRequest request() {
        return AgentClient.AnalyzeRequest.of(UUID.randomUUID(), "hoi", null, List.of());
    }

    @Test
    void imLangQuaIdleTimeout_biCatVaBaoLyDoDeHieu() throws Exception {
        AgentClient client = clientFor(
                write -> {
                    write.accept("event: status\ndata: {\"iteration\":0}\n\n");
                    Thread.sleep(3000);
                },
                Duration.ofMillis(300));
        List<AgentClient.AgentEvent> events = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> client.analyze(request(), events::add))
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessageContaining("Agent không phản hồi trong 300 ms")
                .hasMessageContaining("có thể hỏi lại");
        assertThat(events).extracting(AgentClient.AgentEvent::name).containsExactly("status");
    }

    /**
     * Lượt DÀI hơn idle timeout nhưng agent-service gửi nhịp đều đặn thì không bị cắt — đây chính là
     * ca ba lượt Gemini đã hỏng.
     */
    @Test
    void coNhipDeuDan_luotDaiHonIdleVanXong() throws Exception {
        AgentClient client = clientFor(
                write -> {
                    for (int i = 0; i < 6; i++) {
                        write.accept(": ping\n\n");
                        Thread.sleep(100);
                    }
                    write.accept("event: answer\ndata: {\"answer\":{}}\n\n");
                },
                Duration.ofMillis(300));
        List<AgentClient.AgentEvent> events = new CopyOnWriteArrayList<>();

        client.analyze(request(), events::add);

        assertThat(events).extracting(AgentClient.AgentEvent::name).contains("answer");
        assertThat(events)
                .filteredOn(event -> event.name().equals(AgentClient.AgentEvent.HEARTBEAT))
                .as("moi dong `: ping` thanh mot nhip de chuyen tiep xuong trinh duyet")
                .hasSize(6);
    }

    /**
     * Nút Dừng đóng kết nối qua {@code onConnected}; lượt phải thoát NGAY chứ không đợi agent chạy xong.
     *
     * <p>Lỗi gặp thật 23/11/2026: trước đây {@code onConnected} nhận {@code ClientHttpResponse}, mà
     * {@code close()} của nó đọc cạn phần thân còn lại — tức CHẶN cho tới khi agent-service tự xong.
     */
    @Test
    void dongKetNoiQuaOnConnected_luotThoatNgay() throws Exception {
        AgentClient client = clientFor(
                write -> {
                    write.accept("event: status\ndata: {\"iteration\":0}\n\n");
                    Thread.sleep(3000);
                },
                Duration.ofSeconds(10));
        long started = System.nanoTime();

        assertThatThrownBy(() -> client.analyze(
                        request(),
                        event -> {},
                        connection -> new Thread(() -> {
                                    try {
                                        Thread.sleep(200);
                                        connection.close();
                                    } catch (IOException | InterruptedException ignored) {
                                        // không quan trọng với test
                                    }
                                })
                                .start()))
                .isInstanceOf(AgentUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    /** Sự kiện {@code error} của agent-service giữ nguyên thông điệp của nó, không bị ghi đè. */
    @Test
    void loiTuAgentKhongBiGhiDeThongDiep() throws Exception {
        AgentClient client = clientFor(
                write -> write.accept("event: error\ndata: {\"message\":\"Error code: 503\"}\n\n"),
                Duration.ofMillis(300));

        assertThatThrownBy(() -> client.analyze(request(), event -> {
                    if (event.name().equals(AgentClient.AgentEvent.ERROR)) {
                        throw new AgentUnavailableException("Error code: 503");
                    }
                }))
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessage("Error code: 503");
    }

    @FunctionalInterface
    private interface Writer {
        void accept(String chunk) throws IOException;
    }

    @FunctionalInterface
    private interface Script {
        void run(Writer write) throws IOException, InterruptedException;
    }
}
