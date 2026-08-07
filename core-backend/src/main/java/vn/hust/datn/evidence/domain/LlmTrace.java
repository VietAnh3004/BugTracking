package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một lượt gọi model — tức MỘT VÒNG của agent loop, không phải một câu trả lời.
 *
 * <p>Kế hoạch §4 gọi bảng này là "nguồn cho toàn bộ chương 4 báo cáo, không thể tái tạo sau". Vì
 * vậy nó ghi cả những lượt hỏng: {@link #messageId} bằng {@code null} nghĩa là agent chết trước
 * khi kịp sinh câu trả lời, và chính những dòng đó mới cho biết nó chết ở vòng thứ mấy.
 *
 * <p>agent-service KHÔNG ghi bảng này (nó không có kết nối DB — ADR 0001). Nó trả số đo về trong
 * payload và core-backend ghi cùng transaction với {@link Message}.
 */
@Entity
@Table(name = "llm_trace")
@Getter
@Setter
@NoArgsConstructor
public class LlmTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    /** {@code null} khi lượt hỏi đáp hỏng trước lúc sinh được {@link Message}. */
    @Column(name = "message_id")
    private UUID messageId;

    @Column(nullable = false)
    private int iteration;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    /** Phiên bản prompt đã dùng — thực nghiệm cần biết prompt nào sinh ra kết quả nào. */
    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "stop_reason")
    private String stopReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_messages", nullable = false)
    private List<Map<String, Object>> requestMessages = List.of();

    /** {@code [{name, arguments, duration_ms, item_count}]} — số liệu đo 5 tool cho báo cáo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", nullable = false)
    private List<Map<String, Object>> toolCalls = List.of();

    @Column(name = "response_content")
    private String responseContent;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
