package vn.hust.datn.evidence.dto;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Chỉ số của một lần phân tích — nguồn dữ liệu duy nhất của dashboard VÀ của export.
 *
 * <h2>Mọi tỉ lệ đều là {@code Double} bọc, không phải {@code double}</h2>
 *
 * Đó không phải thói quen mà là quyết định: <b>mẫu số bằng 0 phải trả {@code null}</b>. "Chưa ai
 * review" và "review hết mà 0% khớp" là hai chuyện khác hẳn nhau, và trộn chúng lại là đúng loại
 * hỏng-im-lặng mà cả đồ án này chống — cùng một triết lý với "rỗng là một kết luận" ở tuần 10-11.
 *
 * <p>Giao diện phải render {@code null} thành "—", tuyệt đối không thành {@code 0%}.
 */
@Schema(description = "Chỉ số của một lần phân tích. Tỉ lệ null nghĩa là chưa đủ dữ liệu để tính")
public record RunMetricsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID analysisRunId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CitationQuality citationQuality,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewProgress reviewProgress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) GroundTruthComparison groundTruth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentCost agentCost) {

    /** Chất lượng trích dẫn của Agent — tính từ {@code claim} và {@code claim_citation}. */
    @Schema(description = "Chất lượng trích dẫn của Agent")
    public record CitationQuality(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long answerCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long claimCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long unsupportedClaimCount,
            @Schema(description = "null khi chưa có nhận định nào") Double unsupportedClaimRate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long citationCount,
            @Schema(description = "Số lượt trích dẫn theo từng kết cục: RESOLVED/WRONG_RUN/UNKNOWN_ID/MALFORMED",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    Map<String, Long> citationsByResolution,
            @Schema(description = "null khi chưa có trích dẫn nào") Double citationAccuracy,
            @Schema(description = "Số lượt trả lời bị CitationValidator hạ cấp",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long downgradedAnswerCount) {}

    /**
     * Tiến độ và kết quả review của người dùng.
     *
     * <p>{@code withAgent} và {@code withoutAgent} tách riêng có chủ đích: đó là hai nhóm của user
     * study (kế hoạch §6), gộp lại thì mất đúng phép so sánh mà cả buổi thử sinh ra để làm.
     */
    @Schema(description = "Tiến độ review và mức khớp với Agent")
    public record ReviewProgress(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long warningCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long reviewedCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Long> decisionCounts,
            @Schema(description = "Số review có đọc Agent trước khi quyết", requiredMode = Schema.RequiredMode.REQUIRED)
                    long withAgentCount,
            @Schema(description = "Nhóm đối chứng: quyết định mà không dùng Agent",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long withoutAgentCount,
            @Schema(description = "Tỉ lệ quyết định của người khớp verdict Agent; null khi chưa có review nào có Agent")
                    Double agreementWithAgent) {}

    /**
     * So sánh với nhãn chuẩn — <b>rỗng cho tới tuần 15</b>.
     *
     * <p>Bảng {@code ground_truth_label} dựng ở tuần 14 nhưng chỉ nạp dữ liệu khi gán nhãn xong
     * (kế hoạch §5). Mọi trường ở đây là {@code null} cho tới lúc đó, và giao diện nói thẳng điều
     * đó thay vì hiện 0%.
     */
    @Schema(description = "So sánh với nhãn chuẩn; rỗng cho tới khi gán nhãn ở tuần 15")
    public record GroundTruthComparison(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long labelledCount,
            @Schema(description = "Tỉ lệ verdict Agent khớp nhãn chuẩn; null khi chưa gán nhãn")
                    Double agentAccuracy,
            @Schema(description = "Tỉ lệ quyết định của người khớp nhãn chuẩn; null khi chưa gán nhãn")
                    Double humanAccuracy) {}

    /** Chi phí đọc từ {@code llm_trace} — nguồn số liệu chương 4 của báo cáo. */
    @Schema(description = "Chi phí gọi model")
    public record AgentCost(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long modelCallCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long outputTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long toolCallCount,
            @Schema(description = "null khi chưa gọi model lần nào") Double averageLatencyMs,
            @Schema(description = "Số vòng lặp trung bình mỗi lượt hỏi; null khi chưa có lượt nào")
                    Double averageIterationsPerAnswer,
            @Schema(description = "Số lượt hỏi hỏng — llm_trace có message_id NULL",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long failedTurnCount) {}

    /**
     * Chia an toàn: mẫu số 0 ra {@code null}, KHÔNG ra 0.
     *
     * <p>Một chỗ duy nhất cho phép chia, để không ai vô tình viết {@code (double) a / b} rồi cho
     * ra {@code NaN} hoặc {@code 0.0} tuỳ trình biên dịch thấy vui.
     */
    public static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
