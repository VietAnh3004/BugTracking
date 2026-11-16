package vn.hust.datn.evidence.service.embedding;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import vn.hust.datn.evidence.config.properties.AgentServiceProperties;

/**
 * Gọi {@code POST /embed} của agent-service. Khoá API của nhà cung cấp embedding chỉ nằm ở
 * agent-service — cùng chỗ với khoá LLM, core-backend không cầm bí mật nào của bên thứ ba.
 *
 * <p>GHIM HTTP/1.1 cùng lý do với {@code AgentClient}: client HTTP của JDK mặc định thử nâng cấp
 * h2c và gửi request KHÔNG có thân tới uvicorn — lỗi đã gặp thật ở tuần 12-13.
 */
@Component
public class EmbeddingClient {

    private final RestClient restClient;

    public EmbeddingClient(AgentServiceProperties properties) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.baseUrl())
                .build();
    }

    /**
     * @param task {@code document} cho bằng chứng, {@code query} cho câu hỏi — Gemini nhúng hai
     *     loại khác nhau, trộn lẫn làm giảm chất lượng tìm kiếm
     * @throws EmbeddingUnavailableException kèm lý do đọc được (kể cả thân lỗi của agent-service)
     */
    public Result embed(List<String> texts, String task) {
        try {
            Result result = restClient
                    .post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Request(texts, task))
                    .retrieve()
                    .body(Result.class);
            if (result == null || result.vectors() == null || result.vectors().size() != texts.size()) {
                throw new EmbeddingUnavailableException("agent-service tra ve so vector khong khop so doan van ban");
            }
            return result;
        } catch (RestClientResponseException e) {
            // Kèm thân lỗi: "503" một mình không nói gì, "Embedding chua bat: dat
            // EMBEDDING_PROVIDER=gemini" thì nói luôn cách sửa.
            throw new EmbeddingUnavailableException(
                    "agent-service tra ve %d: %s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()));
        } catch (org.springframework.web.client.RestClientException e) {
            throw new EmbeddingUnavailableException("khong goi duoc agent-service: " + e.getMessage());
        }
    }

    public record Request(List<String> texts, String task) {}

    public record Result(String model, int dimensions, List<List<Double>> vectors) {}
}
