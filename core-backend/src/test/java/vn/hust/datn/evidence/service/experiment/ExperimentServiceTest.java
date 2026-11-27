package vn.hust.datn.evidence.service.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.ExperimentConfig;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.ExperimentStatusResponse;
import vn.hust.datn.evidence.dto.ExperimentStatusResponse.ConfigMetrics;
import vn.hust.datn.evidence.exception.AgentUnavailableException;
import vn.hust.datn.evidence.exception.ExperimentAlreadyRunningException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.service.agent.AgentClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Thực nghiệm 3 cấu hình từ đầu tới cuối, với agent-service là spy (không gọi LLM thật).
 *
 * <p>Spy chứ không mock, cùng lý do với {@code ConversationFlowTest}: {@code parse} chạy thật nên
 * payload lệch hợp đồng giữa hai service sẽ làm test đỏ.
 */
class ExperimentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AgentClient agentClient;

    private User owner;
    private AnalysisRun run;
    private String warningShortId;
    private final List<AgentClient.AnalyzeRequest> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        requests.clear();
        owner = fixtures.user("tn-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("tn-" + System.nanoTime(), owner);
        run = fixtures.succeededRun(fixtures.snapshot(project), 1);
        var warning = fixtures.warning(run, "SQL_INJECTION_JDBC", WarningTool.spotbugs, "Repo.java", 12);
        warningShortId = warning.getEvidenceRecord().getShortId();
        jdbcTemplate.update(
                """
                INSERT INTO ground_truth_label (evidence_record_id, source, label, rationale, labeler)
                VALUES (?, 'MANUAL', 'TRUE_POSITIVE', 'loi that', 'chu')
                """,
                warning.getEvidenceRecord().getId());
    }

    private String answer(String verdict, String citedId) {
        return
                """
                {"answer":{"verdict":"%s","summary":"tom tat",
                 "claims":[{"text":"nhan dinh","evidence_ids":["%s"],"confidence":0.7}],
                 "contradicting_evidence_ids":[],"suggested_next_checks":[]},
                 "traces":[{"iteration":0,"provider":"scripted","model":"model-gia","prompt_version":"v2",
                 "input_tokens":100,"output_tokens":10,"latency_ms":7,"stop_reason":"end_turn",
                 "request_messages":[],"tool_calls":[{"name":"get_warning"}],"response_content":"..."}]}
                """
                        .formatted(verdict, citedId);
    }

    /** LLM-Code trích một ID bịa (bị hạ verdict); hai cấu hình còn lại trích đúng cảnh báo. */
    private void agentAnswers() {
        doAnswer(invocation -> {
                    AgentClient.AnalyzeRequest request = invocation.getArgument(0);
                    requests.add(request);
                    Consumer<AgentClient.AgentEvent> onEvent = invocation.getArgument(1);
                    String body = "llm_code".equals(request.config())
                            ? answer("LIKELY_TRUE_POSITIVE", "EV-ZZZZ")
                            : answer("LIKELY_TRUE_POSITIVE", warningShortId);
                    onEvent.accept(new AgentClient.AgentEvent("answer", body));
                    return null;
                })
                .when(agentClient)
                .analyze(any(), any(), any());
    }

    private void agentFails() {
        doAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    Consumer<AgentClient.AgentEvent> onEvent = invocation.getArgument(1);
                    onEvent.accept(new AgentClient.AgentEvent(
                            "error", "{\"type\":\"error\",\"message\":\"het han muc trong ngay\",\"traces\":[]}"));
                    return null;
                })
                .when(agentClient)
                .analyze(any(), any(), any());
    }

    private ExperimentStatusResponse awaitStopped() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        ExperimentStatusResponse status = experimentService.status(run.getId(), owner.getId());
        while (status.running() && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            status = experimentService.status(run.getId(), owner.getId());
        }
        assertThat(status.running()).as("thuc nghiem phai dung").isFalse();
        return status;
    }

    private ConfigMetrics metricsOf(ExperimentStatusResponse status, ExperimentConfig config) {
        return status.configs().stream().filter(m -> m.config() == config).findFirst().orElseThrow();
    }

    @Test
    void chayDuMoiOVaHauKiemTrichDanNhuAgentThat() {
        agentAnswers();

        experimentService.start(run.getId(), owner.getId());
        ExperimentStatusResponse status = awaitStopped();

        assertThat(status.totalCells()).isEqualTo(9); // 1 cảnh báo × 3 cấu hình × 3 lần
        assertThat(status.succeededCells()).isEqualTo(9);
        assertThat(status.lastStopReason()).contains("chạy xong");
        assertThat(requests)
                .extracting(AgentClient.AnalyzeRequest::config)
                .containsExactly(
                        "llm_code", "llm_static", "evidence_agent",
                        "llm_code", "llm_static", "evidence_agent",
                        "llm_code", "llm_static", "evidence_agent");
        assertThat(requests).allSatisfy(r -> {
            assertThat(r.focusShortId()).isEqualTo(warningShortId);
            assertThat(r.modelTier()).isEqualTo("free");
        });

        // ID bịa bị CitationValidator hạ xuống INSUFFICIENT_EVIDENCE — tính là sai.
        assertThat(metricsOf(status, ExperimentConfig.LLM_CODE).accuracy()).isEqualTo(0.0);
        assertThat(metricsOf(status, ExperimentConfig.LLM_CODE).unsupportedClaimRate()).isEqualTo(1.0);
        assertThat(metricsOf(status, ExperimentConfig.EVIDENCE_AGENT).accuracy()).isEqualTo(1.0);
        assertThat(metricsOf(status, ExperimentConfig.EVIDENCE_AGENT).stability()).isEqualTo(1.0);
        assertThat(metricsOf(status, ExperimentConfig.EVIDENCE_AGENT).avgToolCalls()).isEqualTo(1.0);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM experiment_result
                         WHERE analysis_run_id = ? AND config = 'LLM_CODE'
                           AND verdict = 'INSUFFICIENT_EVIDENCE' AND downgraded_from_verdict = 'LIKELY_TRUE_POSITIVE'
                        """,
                        Integer.class,
                        run.getId()))
                .isEqualTo(3);
    }

    @Test
    void dungSauBaOHongLienTiepRoiChayTiepChiLamOConThieu() {
        agentFails();
        experimentService.start(run.getId(), owner.getId());
        ExperimentStatusResponse stopped = awaitStopped();

        assertThat(requests).hasSize(3);
        assertThat(stopped.failedCells()).isEqualTo(3);
        assertThat(stopped.lastStopReason()).contains("3 ô hỏng liên tiếp").contains("het han muc trong ngay");

        requests.clear();
        agentAnswers();
        experimentService.start(run.getId(), owner.getId());
        ExperimentStatusResponse resumed = awaitStopped();

        assertThat(requests).hasSize(9); // 3 ô hỏng chạy lại + 6 ô chưa chạy
        assertThat(resumed.succeededCells()).isEqualTo(9);
        assertThat(resumed.failedCells()).isZero();

        requests.clear();
        experimentService.start(run.getId(), owner.getId());
        awaitStopped();
        assertThat(requests).as("o da thanh cong khong chay lai").isEmpty();
    }

    @Test
    void dangChayThiBamChayLaiBi409VaDungThiKhongGhiO() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(invocation -> {
                    Consumer<Closeable> onConnected = invocation.getArgument(2);
                    onConnected.accept(closed::countDown);
                    connected.countDown();
                    closed.await(20, TimeUnit.SECONDS);
                    throw new AgentUnavailableException("closed", new IOException("closed"));
                })
                .when(agentClient)
                .analyze(any(), any(), any());

        experimentService.start(run.getId(), owner.getId());
        assertThat(connected.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> experimentService.start(run.getId(), owner.getId()))
                .isInstanceOf(ExperimentAlreadyRunningException.class);

        experimentService.stop(run.getId(), owner.getId());
        ExperimentStatusResponse status = awaitStopped();

        assertThat(status.lastStopReason()).contains("dừng theo yêu cầu");
        assertThat(status.succeededCells() + status.failedCells()).isZero();
    }

    @Test
    void nguoiKhongPhaiChuDuAnNhan404() {
        User stranger = fixtures.user("la-" + System.nanoTime() + "@x.test");
        UUID runId = run.getId();

        assertThatThrownBy(() -> experimentService.status(runId, stranger.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> experimentService.start(runId, stranger.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void csvMoiOMotDongCoBomVaCotDung() {
        agentAnswers();
        experimentService.start(run.getId(), owner.getId());
        awaitStopped();

        String csv = new String(experimentService.csv(run.getId(), owner.getId()), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿short_id,file_path,start_line,tool,rule_id,ground_truth_label,config,repeat");
        List<String> lines = csv.lines().toList();
        assertThat(lines).hasSize(1 + 9);
        assertThat(lines.get(1)).contains(warningShortId).contains("LLM_CODE").contains("INSUFFICIENT_EVIDENCE");
        assertThat(lines.get(3)).contains("EVIDENCE_AGENT").contains("LIKELY_TRUE_POSITIVE").contains("true");
    }
}
