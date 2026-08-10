package vn.hust.datn.evidence.service.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.ReviewDecision;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.RunMetricsResponse;
import vn.hust.datn.evidence.dto.SubmitReviewRequest;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Chỉ số của một lần phân tích.
 *
 * <p>Phần lớn test ở đây canh MỘT quy tắc: <b>mẫu số bằng 0 phải ra {@code null}, không phải
 * {@code 0}</b>. "Chưa ai review" và "review hết mà 0% khớp" là hai chuyện khác hẳn nhau, và
 * trộn chúng lại là đúng loại hỏng-im-lặng mà cả đồ án chống — người chấm sẽ đọc một dashboard
 * toàn 0% và kết luận hệ thống vô dụng, trong khi thật ra chưa ai bấm nút nào.
 */
class RunMetricsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RunMetricsService metrics;

    @Autowired
    private HumanReviewService reviews;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private AnalysisRun run;
    private String shortId;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("chu-run-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 66);
        shortId = warning.getEvidenceRecord().getShortId();
    }

    /** Dựng một lượt trả lời Agent thật, neo vào cảnh báo đang xét. */
    private UUID answer(String verdict, String downgradedFrom) {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, focus_short_id, title) VALUES (?, ?, ?, ?, 'x')",
                conversationId,
                owner.getId(),
                run.getId(),
                shortId);
        jdbcTemplate.update(
                """
                INSERT INTO message (id, conversation_id, ordinal, role, content, verdict, downgraded_from_verdict)
                VALUES (?, ?, 0, 'ASSISTANT', 'tom tat', ?, ?)
                """,
                messageId,
                conversationId,
                verdict,
                downgradedFrom);
        return messageId;
    }

    private UUID claimWithCitations(UUID messageId, int ordinal, String... resolutions) {
        UUID claimId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO claim (id, message_id, ordinal, text, verdict) VALUES (?, ?, ?, 'nhan dinh', 'LIKELY_TRUE_POSITIVE')",
                claimId,
                messageId,
                ordinal);
        for (String resolution : resolutions) {
            // RESOLVED BẮT BUỘC phải trỏ về một evidence thật — CHECK của V2 ép điều đó, và đúng
            // là phải ép: một trích dẫn "đã tra được" mà không có đích là một mâu thuẫn.
            UUID evidenceId = "RESOLVED".equals(resolution)
                    ? jdbcTemplate.queryForObject(
                            "SELECT id FROM evidence_record WHERE analysis_run_id = ? AND short_id = ?",
                            UUID.class,
                            run.getId(),
                            shortId)
                    : null;
            jdbcTemplate.update(
                    """
                    INSERT INTO claim_citation (id, claim_id, cited_short_id_raw, evidence_record_id, resolution)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    claimId,
                    shortId,
                    evidenceId,
                    resolution);
        }
        return claimId;
    }

    /**
     * Run chưa có gì: MỌI tỉ lệ phải là {@code null}, và mọi bộ đếm phải là 0.
     *
     * <p>Đây là test quan trọng nhất của lớp này. Nếu một tỉ lệ ra {@code 0.0}, người chấm mở
     * dashboard sẽ đọc thành "0% chính xác" thay vì "chưa có dữ liệu".
     */
    @Test
    void runChuaCoGi_moiTiLeLaNullKhongPhaiKhong() {
        RunMetricsResponse result = metrics.compute(run.getId(), owner.getId());

        assertThat(result.citationQuality().unsupportedClaimRate()).isNull();
        assertThat(result.citationQuality().citationAccuracy()).isNull();
        assertThat(result.reviewProgress().agreementWithAgent()).isNull();
        assertThat(result.groundTruth().agentAccuracy()).isNull();
        assertThat(result.groundTruth().humanAccuracy()).isNull();
        assertThat(result.agentCost().averageLatencyMs()).isNull();
        assertThat(result.agentCost().averageIterationsPerAnswer()).isNull();

        assertThat(result.citationQuality().claimCount()).isZero();
        assertThat(result.reviewProgress().reviewedCount()).isZero();
        assertThat(result.reviewProgress().warningCount()).isEqualTo(1);
    }

    @Test
    void unsupportedClaimRate_dungDinhNghiaCuaCitationValidator() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        claimWithCitations(messageId, 0, "RESOLVED");
        claimWithCitations(messageId, 1, "UNKNOWN_ID");
        claimWithCitations(messageId, 2);

        RunMetricsResponse.CitationQuality quality =
                metrics.compute(run.getId(), owner.getId()).citationQuality();

        assertThat(quality.claimCount()).isEqualTo(3);
        // Không có trích dẫn RESOLVED nào ⇒ không có căn cứ. Định nghĩa phải khớp TUYỆT ĐỐI với
        // CitationValidator, nếu không dashboard và hành vi hạ cấp verdict nói hai chuyện khác nhau.
        assertThat(quality.unsupportedClaimCount()).isEqualTo(2);
        assertThat(quality.unsupportedClaimRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void citationAccuracy_vaPhanBoBonKetCuc() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        claimWithCitations(messageId, 0, "RESOLVED", "RESOLVED", "UNKNOWN_ID", "WRONG_RUN");

        RunMetricsResponse.CitationQuality quality =
                metrics.compute(run.getId(), owner.getId()).citationQuality();

        assertThat(quality.citationCount()).isEqualTo(4);
        assertThat(quality.citationAccuracy()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(quality.citationsByResolution())
                .containsEntry("RESOLVED", 2L)
                .containsEntry("UNKNOWN_ID", 1L)
                .containsEntry("WRONG_RUN", 1L)
                .containsEntry("MALFORMED", 0L);
    }

    @Test
    void demDuocSoLanHaCapVerdict() {
        answer("INSUFFICIENT_EVIDENCE", "LIKELY_TRUE_POSITIVE");
        answer("LIKELY_TRUE_POSITIVE", null);

        assertThat(metrics.compute(run.getId(), owner.getId())
                        .citationQuality()
                        .downgradedAnswerCount())
                .isEqualTo(1);
    }

    /**
     * Hai nhóm của user study phải TÁCH RIÊNG.
     *
     * <p>Gộp lại thì mất đúng phép so sánh mà cả buổi thử ở tuần 16 sinh ra để làm (kế hoạch §6).
     */
    @Test
    void tachRiengNhomCoAgentVaNhomDoiChung() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        Warning canhBaoHai = fixtures.warning(run, "SQL_INJECTION", WarningTool.semgrep, "UserRepository.java", 30);

        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.ACCEPT, messageId, null, null));
        reviews.submit(
                run.getId(),
                canhBaoHai.getEvidenceRecord().getShortId(),
                owner.getId(),
                new SubmitReviewRequest(ReviewDecision.REJECT, null, null, null));

        RunMetricsResponse.ReviewProgress progress =
                metrics.compute(run.getId(), owner.getId()).reviewProgress();

        assertThat(progress.reviewedCount()).isEqualTo(2);
        assertThat(progress.withAgentCount()).isEqualTo(1);
        assertThat(progress.withoutAgentCount()).isEqualTo(1);
        assertThat(progress.decisionCounts()).containsEntry("ACCEPT", 1L).containsEntry("REJECT", 1L);
    }

    /** Mẫu số của mức khớp là các review CÓ Agent — người không dùng Agent không so được với gì. */
    @Test
    void mucKhopChiTinhTrenNhungReviewCoDocAgent() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.ACCEPT, messageId, null, null));

        assertThat(metrics.compute(run.getId(), owner.getId())
                        .reviewProgress()
                        .agreementWithAgent())
                .isEqualTo(1.0);
    }

    /**
     * {@code UNCERTAIN} luôn tính là KHÔNG khớp nhưng VẪN ở mẫu số.
     *
     * <p>Loại nó ra sẽ làm tỉ lệ đẹp lên một cách giả tạo — vì chính những ca người dùng phân vân
     * mới là ca Agent giúp được ít nhất.
     */
    @Test
    void uncertainKhongKhopNhungVanOMauSo() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.UNCERTAIN, messageId, null, null));

        RunMetricsResponse.ReviewProgress progress =
                metrics.compute(run.getId(), owner.getId()).reviewProgress();

        assertThat(progress.withAgentCount()).isEqualTo(1);
        assertThat(progress.agreementWithAgent()).isZero();
    }

    /** Ground truth rỗng cho tới tuần 15 — phải nói "chưa có", không phải "0% chính xác". */
    @Test
    void groundTruthRong_traVeNullChuKhongPhaiKhong() {
        RunMetricsResponse.GroundTruthComparison truth =
                metrics.compute(run.getId(), owner.getId()).groundTruth();

        assertThat(truth.labelledCount()).isZero();
        assertThat(truth.agentAccuracy()).isNull();
        assertThat(truth.humanAccuracy()).isNull();
    }

    @Test
    void coNhanChuanThiTinhDuocDoChinhXac() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.ACCEPT, messageId, null, null));
        jdbcTemplate.update(
                """
                INSERT INTO ground_truth_label (evidence_record_id, source, label, rationale, labeler)
                SELECT id, 'MANUAL', 'TRUE_POSITIVE', 'Doc ma: findFirst tra ve null', 'toi'
                FROM evidence_record WHERE analysis_run_id = ? AND short_id = ?
                """,
                run.getId(),
                shortId);

        RunMetricsResponse.GroundTruthComparison truth =
                metrics.compute(run.getId(), owner.getId()).groundTruth();

        assertThat(truth.labelledCount()).isEqualTo(1);
        assertThat(truth.agentAccuracy()).isEqualTo(1.0);
        assertThat(truth.humanAccuracy()).isEqualTo(1.0);
    }

    /** Nhãn của người gán mù thứ hai KHÔNG được tính vào accuracy — nó để tính kappa. */
    @Test
    void nhanCuaNguoiGanMuKhongTinhVaoDoChinhXac() {
        jdbcTemplate.update(
                """
                INSERT INTO ground_truth_label (evidence_record_id, source, label, rationale, labeler)
                SELECT id, 'BLIND_SECOND_RATER', 'FALSE_POSITIVE', 'Toi thay khong phai loi', 'nguoi-mu'
                FROM evidence_record WHERE analysis_run_id = ? AND short_id = ?
                """,
                run.getId(),
                shortId);

        assertThat(metrics.compute(run.getId(), owner.getId()).groundTruth().labelledCount())
                .isZero();
    }

    @Test
    void chiPhiDocDuocTuLlmTrace() {
        UUID messageId = answer("LIKELY_TRUE_POSITIVE", null);
        UUID conversationId = jdbcTemplate.queryForObject(
                "SELECT conversation_id FROM message WHERE id = ?", UUID.class, messageId);
        for (int iteration = 0; iteration < 2; iteration++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO llm_trace (id, conversation_id, message_id, iteration, provider, model,
                                           prompt_version, input_tokens, output_tokens, latency_ms, tool_calls)
                    VALUES (?, ?, ?, ?, 'scripted', 'model-gia', 'v1', 100, 20, 500, '[{"name":"get_warning"}]')
                    """,
                    UUID.randomUUID(),
                    conversationId,
                    messageId,
                    iteration);
        }
        // Một lượt HỎNG: message_id NULL. Chính những dòng này cho biết agent chết ở vòng nào.
        jdbcTemplate.update(
                """
                INSERT INTO llm_trace (id, conversation_id, message_id, iteration, provider, model,
                                       prompt_version, latency_ms, error_message)
                VALUES (?, ?, NULL, 0, 'scripted', 'model-gia', 'v1', 100, 'het so vong')
                """,
                UUID.randomUUID(),
                conversationId);

        RunMetricsResponse.AgentCost cost = metrics.compute(run.getId(), owner.getId()).agentCost();

        assertThat(cost.modelCallCount()).isEqualTo(3);
        assertThat(cost.inputTokens()).isEqualTo(200);
        assertThat(cost.outputTokens()).isEqualTo(40);
        assertThat(cost.toolCallCount()).isEqualTo(2);
        assertThat(cost.failedTurnCount()).isEqualTo(1);
        assertThat(cost.averageIterationsPerAnswer()).isEqualTo(2.0);
    }
}
