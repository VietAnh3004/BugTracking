package vn.hust.datn.evidence.service.review;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ReviewDecision;
import vn.hust.datn.evidence.dto.RunMetricsResponse;
import vn.hust.datn.evidence.service.EvidenceQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Tính chỉ số của một lần phân tích — <b>nguồn duy nhất</b> cho cả dashboard lẫn export.
 *
 * <p>Một nguồn duy nhất là quyết định có chủ đích: hai chỗ cùng tính một chỉ số bằng SQL riêng
 * sớm muộn cũng lệch, và lệch ở đây nghĩa là con số trên màn hình khác con số trong file người
 * chấm tải về.
 *
 * <p>Thống kê tính trên <b>toàn bộ</b> review của run, không chỉ của người gọi: chủ run là người
 * chạy thực nghiệm, và ở tuần 16 nhiều người cùng xét một tập cảnh báo. Đường xem quyết định
 * <i>của từng người</i> là {@code GET /reviews} và nó cố ý chỉ trả về của chính người gọi.
 */
@Service
@RequiredArgsConstructor
public class RunMetricsService {

    private final JdbcTemplate jdbcTemplate;
    private final EvidenceQueryService evidenceQueryService;

    @Transactional(readOnly = true)
    public RunMetricsResponse compute(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        evidenceQueryService.ensureRunQueryable(runId);

        return new RunMetricsResponse(runId, citationQuality(runId), reviewProgress(runId), groundTruth(runId), agentCost(runId));
    }

    private RunMetricsResponse.CitationQuality citationQuality(UUID runId) {
        long answerCount = count(
                """
                SELECT count(*) FROM message m
                JOIN conversation c ON c.id = m.conversation_id
                WHERE c.analysis_run_id = ? AND m.role = 'ASSISTANT'
                """,
                runId);
        long downgraded = count(
                """
                SELECT count(*) FROM message m
                JOIN conversation c ON c.id = m.conversation_id
                WHERE c.analysis_run_id = ? AND m.downgraded_from_verdict IS NOT NULL
                """,
                runId);
        long claimCount = count(claimScoped("SELECT count(*)"), runId);
        // Nhận định KHÔNG có căn cứ = không có trích dẫn nào RESOLVED. Đây là TỬ SỐ của
        // Unsupported Claim Rate — định nghĩa phải khớp tuyệt đối với CitationValidator, nếu
        // không thì con số trên dashboard và hành vi hạ cấp verdict nói hai chuyện khác nhau.
        long unsupported = count(
                claimScoped("SELECT count(*)")
                        + """
                          AND NOT EXISTS (
                              SELECT 1 FROM claim_citation cc
                              WHERE cc.claim_id = cl.id AND cc.resolution = 'RESOLVED'
                          )
                          """,
                runId);

        Map<String, Long> byResolution = new LinkedHashMap<>();
        for (ClaimCitationResolution resolution : ClaimCitationResolution.values()) {
            byResolution.put(
                    resolution.name(),
                    count(
                            """
                            SELECT count(*) FROM claim_citation cc
                            JOIN claim cl ON cl.id = cc.claim_id
                            JOIN message m ON m.id = cl.message_id
                            JOIN conversation c ON c.id = m.conversation_id
                            WHERE c.analysis_run_id = ? AND cc.resolution = ?
                            """,
                            runId,
                            resolution.name()));
        }
        long citationCount = byResolution.values().stream().mapToLong(Long::longValue).sum();
        long resolved = byResolution.getOrDefault(ClaimCitationResolution.RESOLVED.name(), 0L);

        return new RunMetricsResponse.CitationQuality(
                answerCount,
                claimCount,
                unsupported,
                RunMetricsResponse.ratio(unsupported, claimCount),
                citationCount,
                byResolution,
                RunMetricsResponse.ratio(resolved, citationCount),
                downgraded);
    }

    private RunMetricsResponse.ReviewProgress reviewProgress(UUID runId) {
        long warningCount = count(
                """
                SELECT count(*) FROM evidence_record
                WHERE analysis_run_id = ? AND type = 'STATIC_WARNING'
                """,
                runId);
        long reviewed = count("SELECT count(*) FROM human_review WHERE analysis_run_id = ?", runId);

        Map<String, Long> decisions = new LinkedHashMap<>();
        for (ReviewDecision decision : ReviewDecision.values()) {
            decisions.put(
                    decision.name(),
                    count("SELECT count(*) FROM human_review WHERE analysis_run_id = ? AND decision = ?", runId, decision.name()));
        }

        long withAgent = count(
                "SELECT count(*) FROM human_review WHERE analysis_run_id = ? AND message_id IS NOT NULL", runId);
        // Ba cặp khớp được; UNCERTAIN không có verdict Agent tương ứng nên luôn tính là KHÔNG
        // khớp — nhưng VẪN NẰM TRONG MẪU SỐ. Loại nó ra sẽ làm tỉ lệ đẹp lên một cách giả tạo,
        // vì chính những ca người dùng phân vân mới là ca Agent giúp được ít nhất.
        long agreed = count(
                """
                SELECT count(*) FROM human_review r
                JOIN message m ON m.id = r.message_id
                WHERE r.analysis_run_id = ?
                  AND ((r.decision = 'ACCEPT'               AND m.verdict = 'LIKELY_TRUE_POSITIVE')
                    OR (r.decision = 'REJECT'               AND m.verdict = 'LIKELY_FALSE_POSITIVE')
                    OR (r.decision = 'INSUFFICIENT_EVIDENCE' AND m.verdict = 'INSUFFICIENT_EVIDENCE'))
                """,
                runId);

        return new RunMetricsResponse.ReviewProgress(
                warningCount,
                reviewed,
                decisions,
                withAgent,
                reviewed - withAgent,
                RunMetricsResponse.ratio(agreed, withAgent));
    }

    /**
     * So sánh với nhãn chuẩn — rỗng cho tới khi tuần 15 gán nhãn.
     *
     * <p>Chỉ lấy {@code source = 'MANUAL'} và {@code 'SEEDED'}: nhãn của người gán mù thứ hai tồn
     * tại để tính Cohen's kappa, đưa vào đây sẽ tính accuracy hai lần trên cùng một cảnh báo.
     */
    private RunMetricsResponse.GroundTruthComparison groundTruth(UUID runId) {
        long labelled = count(
                """
                SELECT count(*) FROM ground_truth_label g
                JOIN evidence_record e ON e.id = g.evidence_record_id
                WHERE e.analysis_run_id = ? AND g.source IN ('MANUAL', 'SEEDED')
                """,
                runId);
        if (labelled == 0) {
            return new RunMetricsResponse.GroundTruthComparison(0, null, null);
        }

        long agentTotal = count(agentVsTruth("SELECT count(*)"), runId);
        long agentMatched = count(
                agentVsTruth("SELECT count(*)")
                        + """
                          AND ((m.verdict = 'LIKELY_TRUE_POSITIVE'  AND g.label = 'TRUE_POSITIVE')
                            OR (m.verdict = 'LIKELY_FALSE_POSITIVE' AND g.label = 'FALSE_POSITIVE'))
                          """,
                runId);

        long humanTotal = count(humanVsTruth("SELECT count(*)"), runId);
        long humanMatched = count(
                humanVsTruth("SELECT count(*)")
                        + """
                          AND ((r.decision = 'ACCEPT' AND g.label = 'TRUE_POSITIVE')
                            OR (r.decision = 'REJECT' AND g.label = 'FALSE_POSITIVE'))
                          """,
                runId);

        return new RunMetricsResponse.GroundTruthComparison(
                labelled,
                RunMetricsResponse.ratio(agentMatched, agentTotal),
                RunMetricsResponse.ratio(humanMatched, humanTotal));
    }

    private RunMetricsResponse.AgentCost agentCost(UUID runId) {
        String scope =
                """
                FROM llm_trace t
                JOIN conversation c ON c.id = t.conversation_id
                WHERE c.analysis_run_id = ?
                """;
        long calls = count("SELECT count(*) " + scope, runId);
        long inputTokens = count("SELECT COALESCE(sum(t.input_tokens), 0) " + scope, runId);
        long outputTokens = count("SELECT COALESCE(sum(t.output_tokens), 0) " + scope, runId);
        long toolCalls = count("SELECT COALESCE(sum(jsonb_array_length(t.tool_calls)), 0) " + scope, runId);
        long failedTurns = count("SELECT count(*) " + scope + " AND t.message_id IS NULL", runId);

        Double avgLatency = jdbcTemplate.queryForObject("SELECT avg(t.latency_ms) " + scope, Double.class, runId);
        long answers = count(
                """
                SELECT count(DISTINCT t.message_id) FROM llm_trace t
                JOIN conversation c ON c.id = t.conversation_id
                WHERE c.analysis_run_id = ? AND t.message_id IS NOT NULL
                """,
                runId);

        return new RunMetricsResponse.AgentCost(
                calls,
                inputTokens,
                outputTokens,
                toolCalls,
                calls == 0 ? null : avgLatency,
                RunMetricsResponse.ratio(calls - failedTurns, answers),
                failedTurns);
    }

    private static String claimScoped(String select) {
        return select
                + """
                  FROM claim cl
                  JOIN message m ON m.id = cl.message_id
                  JOIN conversation c ON c.id = m.conversation_id
                  WHERE c.analysis_run_id = ?
                  """;
    }

    private static String agentVsTruth(String select) {
        // Verdict của Agent về MỘT cảnh báo = verdict của lượt trả lời trong hội thoại neo vào
        // đúng cảnh báo đó (conversation.focus_short_id). Hội thoại hỏi ở phạm vi cả run không
        // nói về một cảnh báo cụ thể nào nên không so được — cố ý loại khỏi mẫu số.
        return select
                + """
                  FROM ground_truth_label g
                  JOIN evidence_record e ON e.id = g.evidence_record_id
                  JOIN conversation c ON c.analysis_run_id = e.analysis_run_id AND c.focus_short_id = e.short_id
                  JOIN message m ON m.conversation_id = c.id AND m.role = 'ASSISTANT'
                  WHERE e.analysis_run_id = ? AND g.source IN ('MANUAL', 'SEEDED')
                  """;
    }

    private static String humanVsTruth(String select) {
        return select
                + """
                  FROM ground_truth_label g
                  JOIN evidence_record e ON e.id = g.evidence_record_id
                  JOIN human_review r ON r.evidence_record_id = e.id
                  WHERE e.analysis_run_id = ? AND g.source IN ('MANUAL', 'SEEDED')
                  """;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
