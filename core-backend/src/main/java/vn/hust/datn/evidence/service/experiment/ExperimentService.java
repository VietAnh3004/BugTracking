package vn.hust.datn.evidence.service.experiment;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.config.properties.ExperimentProperties;
import vn.hust.datn.evidence.domain.ExperimentConfig;
import vn.hust.datn.evidence.dto.ExperimentStatusResponse;
import vn.hust.datn.evidence.exception.AgentUnavailableException;
import vn.hust.datn.evidence.exception.ExperimentAlreadyRunningException;
import vn.hust.datn.evidence.service.EvidenceQueryService;
import vn.hust.datn.evidence.service.agent.AgentAnswer;
import vn.hust.datn.evidence.service.agent.AgentClient;
import vn.hust.datn.evidence.service.citation.CitationValidator;
import vn.hust.datn.evidence.service.citation.ValidatedAnswer;
import vn.hust.datn.evidence.service.review.RunExportService;

/**
 * Thực nghiệm 3 cấu hình (kế hoạch §6, {@code docs/thuc-nghiem.md}): mọi cảnh báo của một run ×
 * {@link ExperimentConfig} × {@code app.experiment.repeats} lần lặp, mỗi tổ hợp là một Ô.
 *
 * <h2>Chạy rải nhiều ngày</h2>
 *
 * Trên Gemini free một lượt đủ ~300 ô không xong trong một ngày. Vì vậy mỗi ô được GHI NGAY khi
 * xong vào {@code experiment_result}, và "Chạy tiếp" bỏ qua ô đã thành công, chạy lại ô hỏng. Thứ
 * tự là lần lặp → cảnh báo → cấu hình: dừng ở đâu thì số ô của ba cấu hình vẫn cân nhau, nên số liệu
 * dở dang vẫn so được.
 *
 * <h2>Cùng hậu kiểm với Agent thật</h2>
 *
 * Câu trả lời của cả ba cấu hình đi qua đúng {@link CitationValidator} và đúng
 * {@link AgentAnswer.Answer#toDraft()} như hỏi đáp thường; khác một chỗ là chúng KHÔNG ghi vào
 * {@code conversation}/{@code message} (xem V16).
 *
 * <p>Trạng thái "đang chạy" chỉ nằm trong bộ nhớ: khởi động lại core-backend là job dừng, dữ liệu đã
 * ghi vẫn còn, bấm "Chạy tiếp" là đi tiếp từ ô dở.
 */
@Service
public class ExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

    private static final String[] CSV_HEADERS = {
        "short_id",
        "file_path",
        "start_line",
        "tool",
        "rule_id",
        "ground_truth_label",
        "config",
        "repeat",
        "status",
        "verdict",
        "downgraded_from",
        "correct",
        "claim_count",
        "unsupported_claim_count",
        "citation_count",
        "citation_resolved_count",
        "llm_calls",
        "tool_calls",
        "input_tokens",
        "output_tokens",
        "latency_ms",
        "model",
        "prompt_version",
        "error_message"
    };

    private final JdbcTemplate jdbcTemplate;
    private final AgentClient agentClient;
    private final CitationValidator citationValidator;
    private final EvidenceQueryService evidenceQueryService;
    private final ObjectMapper objectMapper;
    private final ExperimentProperties properties;
    private final Executor executor;

    /** Job đang chạy (hoặc đang xếp hàng) theo run. */
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    /** Lý do dừng của lượt chạy gần nhất, để thẻ trên Dashboard nói được vì sao nó đứng. */
    private final Map<UUID, String> lastStopReasons = new ConcurrentHashMap<>();

    // Constructor viết tay: Lombok không chép @Qualifier sang tham số (CLAUDE.md).
    public ExperimentService(
            JdbcTemplate jdbcTemplate,
            AgentClient agentClient,
            CitationValidator citationValidator,
            EvidenceQueryService evidenceQueryService,
            ObjectMapper objectMapper,
            ExperimentProperties properties,
            @Qualifier("experimentExecutor") Executor executor) {
        this.jdbcTemplate = jdbcTemplate;
        this.agentClient = agentClient;
        this.citationValidator = citationValidator;
        this.evidenceQueryService = evidenceQueryService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = executor;
    }

    /** Chạy (hoặc chạy tiếp) thực nghiệm của một run. Chỉ chủ dự án. */
    public ExperimentStatusResponse start(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        evidenceQueryService.ensureRunQueryable(runId);
        Job job = new Job();
        if (jobs.putIfAbsent(runId, job) != null) {
            throw new ExperimentAlreadyRunningException(runId);
        }
        lastStopReasons.remove(runId);
        try {
            executor.execute(() -> runJob(runId, job));
        } catch (RejectedExecutionException e) {
            jobs.remove(runId, job);
            throw new AgentUnavailableException("Hàng đợi thực nghiệm đã đầy, hãy thử lại sau.", e);
        }
        return status(runId, ownerId);
    }

    /** Dừng sau ô đang chạy — đóng kết nối tới agent-service để không gọi LLM thêm. */
    public ExperimentStatusResponse stop(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        Job job = jobs.get(runId);
        if (job != null) {
            job.stop();
        }
        return status(runId, ownerId);
    }

    public ExperimentStatusResponse status(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        long warningCount = warnings(runId).size();
        List<ExperimentMetrics.Row> rows = jdbcTemplate.query(
                """
                SELECT r.evidence_record_id, r.config, r.repeat_index, r.status, r.verdict,
                       r.claim_count, r.unsupported_claim_count, r.citation_count, r.citation_resolved_count,
                       r.llm_calls, r.tool_calls, r.input_tokens, r.output_tokens, r.latency_ms,
                       gt.label AS ground_truth
                  FROM experiment_result r
                  LEFT JOIN ground_truth_label gt
                         ON gt.evidence_record_id = r.evidence_record_id AND gt.source = 'MANUAL'
                 WHERE r.analysis_run_id = ?
                """,
                (rs, i) -> new ExperimentMetrics.Row(
                        rs.getObject("evidence_record_id", UUID.class),
                        ExperimentConfig.valueOf(rs.getString("config")),
                        rs.getInt("repeat_index"),
                        "SUCCEEDED".equals(rs.getString("status")),
                        rs.getString("verdict"),
                        rs.getString("ground_truth"),
                        rs.getInt("claim_count"),
                        rs.getInt("unsupported_claim_count"),
                        rs.getInt("citation_count"),
                        rs.getInt("citation_resolved_count"),
                        rs.getInt("llm_calls"),
                        rs.getInt("tool_calls"),
                        rs.getInt("input_tokens"),
                        rs.getInt("output_tokens"),
                        rs.getInt("latency_ms")),
                runId);

        Job job = jobs.get(runId);
        return new ExperimentStatusResponse(
                runId,
                job != null,
                properties.repeats(),
                warningCount * ExperimentConfig.values().length * properties.repeats(),
                rows.stream().filter(ExperimentMetrics.Row::succeeded).count(),
                rows.stream().filter(r -> !r.succeeded()).count(),
                job == null ? null : job.currentCell,
                lastStopReasons.get(runId),
                ExperimentMetrics.compute(rows, properties.repeats()));
    }

    /** Mỗi ô một dòng, BOM UTF-8 để Excel đọc đúng tiếng Việt (xem {@link RunExportService}). */
    public byte[] csv(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT r.short_id, e.file_path, e.start_line, w.tool, w.rule_id_normalized AS rule_id,
                       gt.label AS ground_truth_label, r.config, r.repeat_index AS repeat, r.status, r.verdict,
                       r.downgraded_from_verdict AS downgraded_from,
                       CASE WHEN r.status <> 'SUCCEEDED' OR gt.label NOT IN ('TRUE_POSITIVE', 'FALSE_POSITIVE')
                                 OR gt.label IS NULL THEN NULL
                            ELSE (r.verdict = 'LIKELY_TRUE_POSITIVE' AND gt.label = 'TRUE_POSITIVE')
                              OR (r.verdict = 'LIKELY_FALSE_POSITIVE' AND gt.label = 'FALSE_POSITIVE')
                       END AS correct,
                       r.claim_count, r.unsupported_claim_count, r.citation_count, r.citation_resolved_count,
                       r.llm_calls, r.tool_calls, r.input_tokens, r.output_tokens, r.latency_ms,
                       r.model, r.prompt_version, r.error_message
                  FROM experiment_result r
                  JOIN evidence_record e ON e.id = r.evidence_record_id
                  JOIN warning w ON w.evidence_record_id = e.id
                  LEFT JOIN ground_truth_label gt ON gt.evidence_record_id = e.id AND gt.source = 'MANUAL'
                 WHERE r.analysis_run_id = ?
                 ORDER BY r.repeat_index, w.severity_rank, r.short_id,
                          CASE r.config WHEN 'LLM_CODE' THEN 0 WHEN 'LLM_STATIC' THEN 1 ELSE 2 END
                """,
                runId);
        StringWriter out = new StringWriter();
        out.write(RunExportService.UTF8_BOM);
        try (CSVPrinter printer =
                new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build())) {
            for (Map<String, Object> row : rows) {
                List<Object> values = new ArrayList<>(CSV_HEADERS.length);
                for (String header : CSV_HEADERS) {
                    values.add(row.get(header));
                }
                printer.printRecord(values);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String csvFileName(UUID runId) {
        return "thuc-nghiem-%s.csv".formatted(runId);
    }

    // ------------------------------------------------------------------------------------------

    private void runJob(UUID runId, Job job) {
        String stopReason = null;
        try {
            Set<String> done = new HashSet<>(jdbcTemplate.queryForList(
                    """
                    SELECT evidence_record_id || '|' || config || '|' || repeat_index
                      FROM experiment_result WHERE analysis_run_id = ? AND status = 'SUCCEEDED'
                    """,
                    String.class,
                    runId));
            List<WarningRef> warnings = warnings(runId);
            int consecutiveFailures = 0;
            outer:
            for (int repeat = 1; repeat <= properties.repeats(); repeat++) {
                for (WarningRef warning : warnings) {
                    for (ExperimentConfig config : ExperimentConfig.values()) {
                        if (done.contains(warning.evidenceRecordId() + "|" + config.name() + "|" + repeat)) {
                            continue;
                        }
                        if (job.stopRequested) {
                            stopReason = "Đã dừng theo yêu cầu.";
                            break outer;
                        }
                        job.currentCell = "%s · %s · lần %d".formatted(warning.shortId(), config.name(), repeat);
                        CellOutcome outcome = runCell(runId, warning, config, repeat, job);
                        if (outcome == CellOutcome.CANCELLED) {
                            stopReason = "Đã dừng theo yêu cầu.";
                            break outer;
                        }
                        if (outcome == CellOutcome.SUCCEEDED) {
                            consecutiveFailures = 0;
                        } else if (++consecutiveFailures >= properties.maxConsecutiveFailures()) {
                            stopReason = ("Dừng sau %d ô hỏng liên tiếp (trên bản free thường là hết hạn mức model "
                                            + "trong ngày). Lỗi cuối: %s")
                                    .formatted(consecutiveFailures, job.lastError);
                            break outer;
                        }
                    }
                }
            }
            if (stopReason == null) {
                Integer failed = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM experiment_result WHERE analysis_run_id = ? AND status = 'FAILED'",
                        Integer.class,
                        runId);
                stopReason = failed == null || failed == 0
                        ? "Đã chạy xong mọi ô."
                        : "Đã đi hết lượt; còn %d ô hỏng, bấm Chạy tiếp để chạy lại chúng.".formatted(failed);
            }
        } catch (RuntimeException e) {
            log.error("thuc nghiem cua run {} dung vi loi noi bo", runId, e);
            stopReason = "Lỗi nội bộ: " + e.getMessage();
        } finally {
            lastStopReasons.put(runId, stopReason);
            jobs.remove(runId, job);
        }
    }

    private CellOutcome runCell(UUID runId, WarningRef warning, ExperimentConfig config, int repeat, Job job) {
        List<AgentAnswer.Trace> traces = new ArrayList<>();
        AgentAnswer[] answer = new AgentAnswer[1];
        try {
            agentClient.analyze(
                    AgentClient.AnalyzeRequest.forExperiment(runId, warning.shortId(), config),
                    event -> {
                        switch (event.name()) {
                            case AgentClient.AgentEvent.ANSWER -> answer[0] = agentClient.parse(event, AgentAnswer.class);
                            case AgentClient.AgentEvent.ERROR -> {
                                ErrorPayload payload = parseError(event);
                                traces.addAll(payload.tracesOrEmpty());
                                throw new AgentUnavailableException(payload.messageOr(event.data()));
                            }
                            default -> {
                                // status/tool_call/retry/heartbeat: chỉ là tiến trình, không ai xem.
                            }
                        }
                    },
                    job::attach);
        } catch (AgentUnavailableException e) {
            if (job.stopRequested) {
                return CellOutcome.CANCELLED; // ô bị huỷ có chủ đích: không ghi gì, Chạy tiếp làm lại
            }
            return failed(runId, warning, config, repeat, e.getMessage(), traces, job);
        } catch (RuntimeException e) {
            log.warn("o thuc nghiem {} {} lan {} hong", warning.shortId(), config, repeat, e);
            return failed(runId, warning, config, repeat, e.toString(), traces, job);
        } finally {
            job.connection = null;
        }

        if (answer[0] == null || answer[0].answer() == null) {
            return failed(
                    runId, warning, config, repeat, "agent-service dong stream ma khong tra cau tra loi", traces, job);
        }
        ValidatedAnswer validated = citationValidator.validate(runId, answer[0].answer().toDraft());
        save(runId, warning, config, repeat, validated, answer[0].tracesOrEmpty(), null);
        return CellOutcome.SUCCEEDED;
    }

    private CellOutcome failed(
            UUID runId,
            WarningRef warning,
            ExperimentConfig config,
            int repeat,
            String error,
            List<AgentAnswer.Trace> traces,
            Job job) {
        log.warn("o thuc nghiem {} {} lan {} hong: {}", warning.shortId(), config, repeat, error);
        job.lastError = error;
        save(runId, warning, config, repeat, null, traces, error == null ? "loi khong ro" : error);
        return CellOutcome.FAILED;
    }

    /** Ghi (hoặc ghi đè ô hỏng cũ) một ô. {@code validated == null} nghĩa là ô hỏng. */
    private void save(
            UUID runId,
            WarningRef warning,
            ExperimentConfig config,
            int repeat,
            ValidatedAnswer validated,
            List<AgentAnswer.Trace> traces,
            String error) {
        AgentAnswer.Trace first = traces.isEmpty() ? null : traces.getFirst();
        jdbcTemplate.update(
                """
                INSERT INTO experiment_result
                    (analysis_run_id, evidence_record_id, short_id, config, repeat_index, status, verdict,
                     downgraded_from_verdict, claim_count, unsupported_claim_count, citation_count,
                     citation_resolved_count, answer, model, prompt_version, llm_calls, tool_calls,
                     input_tokens, output_tokens, latency_ms, error_message, traces, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                ON CONFLICT (analysis_run_id, evidence_record_id, config, repeat_index) DO UPDATE SET
                    status = EXCLUDED.status, verdict = EXCLUDED.verdict,
                    downgraded_from_verdict = EXCLUDED.downgraded_from_verdict,
                    claim_count = EXCLUDED.claim_count, unsupported_claim_count = EXCLUDED.unsupported_claim_count,
                    citation_count = EXCLUDED.citation_count,
                    citation_resolved_count = EXCLUDED.citation_resolved_count, answer = EXCLUDED.answer,
                    model = EXCLUDED.model, prompt_version = EXCLUDED.prompt_version,
                    llm_calls = EXCLUDED.llm_calls, tool_calls = EXCLUDED.tool_calls,
                    input_tokens = EXCLUDED.input_tokens, output_tokens = EXCLUDED.output_tokens,
                    latency_ms = EXCLUDED.latency_ms, error_message = EXCLUDED.error_message,
                    traces = EXCLUDED.traces, created_at = EXCLUDED.created_at
                """,
                runId,
                warning.evidenceRecordId(),
                warning.shortId(),
                config.name(),
                repeat,
                validated == null ? "FAILED" : "SUCCEEDED",
                validated == null ? null : validated.verdict().name(),
                validated == null || validated.downgradedFrom() == null
                        ? null
                        : validated.downgradedFrom().name(),
                validated == null ? 0 : validated.claims().size(),
                validated == null ? 0 : (int) validated.unsupportedClaimCount(),
                validated == null ? 0 : (int) validated.citationCount(),
                validated == null ? 0 : (int) validated.resolvedCitationCount(),
                validated == null ? null : json(validated),
                first == null ? null : first.model(),
                first == null ? null : first.promptVersion(),
                traces.size(),
                traces.stream().mapToInt(t -> t.toolCallsOrEmpty().size()).sum(),
                traces.stream().mapToInt(AgentAnswer.Trace::inputTokens).sum(),
                traces.stream().mapToInt(AgentAnswer.Trace::outputTokens).sum(),
                traces.stream().mapToInt(AgentAnswer.Trace::latencyMs).sum(),
                error,
                json(traces));
    }

    /** Cảnh báo của run, đúng thứ tự của bảng cảnh báo và của file export. */
    private List<WarningRef> warnings(UUID runId) {
        return jdbcTemplate.query(
                """
                SELECT e.id, e.short_id FROM evidence_record e
                  JOIN warning w ON w.evidence_record_id = e.id
                 WHERE e.analysis_run_id = ?
                 ORDER BY w.severity_rank, e.short_id
                """,
                (rs, i) -> new WarningRef(rs.getObject("id", UUID.class), rs.getString("short_id")),
                runId);
    }

    private ErrorPayload parseError(AgentClient.AgentEvent event) {
        try {
            return agentClient.parse(event, ErrorPayload.class);
        } catch (RuntimeException e) {
            return new ErrorPayload(null, null);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("khong ghi duoc JSON cho experiment_result", e);
        }
    }

    record WarningRef(UUID evidenceRecordId, String shortId) {}

    record ErrorPayload(String message, List<AgentAnswer.Trace> traces) {

        List<AgentAnswer.Trace> tracesOrEmpty() {
            return traces == null ? List.of() : traces;
        }

        String messageOr(String fallback) {
            return message == null || message.isBlank() ? fallback : message;
        }
    }

    private enum CellOutcome {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    /** Một lượt chạy. Mọi trường đọc chéo luồng (luồng thực nghiệm ↔ luồng request) nên volatile. */
    static final class Job {
        volatile boolean stopRequested;
        volatile Closeable connection;
        volatile String currentCell;
        volatile String lastError;

        void attach(Closeable opened) {
            connection = opened;
            if (stopRequested) {
                closeQuietly(opened);
            }
        }

        void stop() {
            stopRequested = true;
            closeQuietly(connection);
        }

        private static void closeQuietly(Closeable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Đang đóng để huỷ: lỗi khi đóng không đổi được gì.
            }
        }
    }
}
