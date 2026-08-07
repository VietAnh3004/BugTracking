package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Một lượt trong hội thoại: câu hỏi của người dùng, hoặc câu trả lời của Agent.
 *
 * <p>Lượt của Agent không phải văn bản tự do — nó luôn có {@link #verdict} và một danh sách
 * {@link Claim} ở bảng riêng. {@link #downgradedFromVerdict} khác {@code null} nghĩa là
 * CitationValidator đã hạ cấp lượt này vì không nhận định nào có trích dẫn hợp lệ; verdict gốc
 * được giữ lại để tuần 15 đếm được bao nhiêu lần hạ cấp đã xảy ra.
 */
@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    /** Câu hỏi của người dùng, hoặc câu tóm tắt của Agent. Toàn văn nhận định nằm ở {@link Claim}. */
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column
    private ClaimVerdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "downgraded_from_verdict")
    private ClaimVerdict downgradedFromVerdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_next_checks", nullable = false)
    private List<String> suggestedNextChecks = List.of();

    /**
     * Bằng chứng đi ngược lại kết luận, nguyên văn LLM viết ra.
     *
     * <p>CÓ CHỦ ĐÍCH không đi qua {@link ClaimCitation}: đây không phải nhận định, nên đưa vào sẽ
     * làm mẫu số của Unsupported Claim Rate lẫn lộn hai thứ khác nhau.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contradicting_short_ids", nullable = false)
    private List<String> contradictingShortIds = List.of();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
