package vn.hust.datn.evidence.service.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import vn.hust.datn.evidence.domain.ClaimVerdict;

/**
 * Payload của sự kiện {@code answer} do agent-service phát ra — câu trả lời NGUYÊN BẢN của mô
 * hình, chưa qua hậu kiểm trích dẫn.
 *
 * <p>Kiểu này KHÔNG ra tới frontend. Nó vào {@link vn.hust.datn.evidence.service.citation.CitationValidator}
 * trước, và thứ người dùng nhận là kết quả sau hậu kiểm (ADR 0010).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} có chủ đích: agent-service thêm trường
 * mới vào sự kiện không được phép làm hỏng một core-backend chưa kịp deploy lại. Chiều ngược
 * lại — thiếu trường bắt buộc — vẫn hỏng, và đúng là phải hỏng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentAnswer(Answer answer, List<Trace> traces) {

    /** Câu trả lời của mô hình, đúng lược đồ {@code AnalysisResponse} của agent-service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(
            ClaimVerdict verdict,
            String summary,
            List<Claim> claims,
            @JsonProperty("contradicting_evidence_ids") List<String> contradictingEvidenceIds,
            @JsonProperty("suggested_next_checks") List<String> suggestedNextChecks) {

        public List<Claim> claimsOrEmpty() {
            return claims == null ? List.of() : claims;
        }

        public List<String> contradictingOrEmpty() {
            return contradictingEvidenceIds == null ? List.of() : contradictingEvidenceIds;
        }

        public List<String> suggestedOrEmpty() {
            return suggestedNextChecks == null ? List.of() : suggestedNextChecks;
        }
    }

    /** Một nhận định kèm danh sách Evidence ID nguyên văn mô hình viết ra. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Claim(
            String text, @JsonProperty("evidence_ids") List<String> evidenceIds, BigDecimal confidence) {

        public List<String> evidenceIdsOrEmpty() {
            return evidenceIds == null ? List.of() : evidenceIds;
        }
    }

    /** Số đo một vòng gọi model, ghi thẳng vào bảng {@code llm_trace}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trace(
            int iteration,
            String provider,
            String model,
            @JsonProperty("prompt_version") String promptVersion,
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("latency_ms") int latencyMs,
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("request_messages") List<Map<String, Object>> requestMessages,
            @JsonProperty("tool_calls") List<Map<String, Object>> toolCalls,
            @JsonProperty("response_content") String responseContent,
            @JsonProperty("error_message") String errorMessage) {

        public List<Map<String, Object>> requestMessagesOrEmpty() {
            return requestMessages == null ? List.of() : requestMessages;
        }

        public List<Map<String, Object>> toolCallsOrEmpty() {
            return toolCalls == null ? List.of() : toolCalls;
        }
    }

    public List<Trace> tracesOrEmpty() {
        return traces == null ? List.of() : traces;
    }
}
