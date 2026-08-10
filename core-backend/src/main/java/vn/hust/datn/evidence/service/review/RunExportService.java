package vn.hust.datn.evidence.service.review;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.dto.RunMetricsResponse;

import lombok.RequiredArgsConstructor;

/**
 * Xuất kết quả một lần phân tích ra CSV và JSON.
 *
 * <p>Hai định dạng phục vụ hai việc khác nhau: CSV một dòng một cảnh báo cho phân tích thống kê ở
 * tuần 17-18 (kế hoạch nhắc tới {@code results.csv}); JSON mang đủ chỉ số và chi tiết để truy lại
 * mà không phải viết SQL tay.
 *
 * <p>Chỉ số KHÔNG tính lại ở đây mà gọi {@link RunMetricsService} — hai chỗ cùng tính một chỉ số
 * sớm muộn cũng lệch, và lệch ở đây nghĩa là con số trên màn hình khác con số trong file tải về.
 */
@Service
@RequiredArgsConstructor
public class RunExportService {

    /**
     * BOM UTF-8 — BẮT BUỘC, không phải trang trí.
     *
     * <p>Excel trên Windows đoán bảng mã theo locale hệ thống chứ không mặc định UTF-8, nên file
     * không BOM mở ra là chữ rác với mọi cột tiếng Việt. Người chấm sẽ mở bằng Excel.
     */
    private static final String UTF8_BOM = "﻿";

    private static final String[] CSV_HEADERS = {
        "short_id",
        "file_path",
        "start_line",
        "tool",
        "rule_id",
        "severity",
        "agent_verdict",
        "agent_downgraded_from",
        "claim_count",
        "unsupported_claim_count",
        "citation_count",
        "citation_resolved_count",
        "human_decision",
        "human_used_agent",
        "human_seconds_spent",
        "human_note",
        "ground_truth_label"
    };

    private final JdbcTemplate jdbcTemplate;
    private final RunMetricsService runMetricsService;

    /**
     * Một dòng một cảnh báo.
     *
     * <p>Trả {@code String} chứ không stream: một run của vulnshop có 34 cảnh báo, và ngay cả một
     * repo lớn cũng chỉ vài nghìn dòng. Đổi sang stream khi có bằng chứng là cần, không phải trước.
     */
    @Transactional(readOnly = true)
    public String toCsv(UUID runId, UUID ownerId) {
        List<Map<String, Object>> rows = warningRows(runId, ownerId);

        StringWriter out = new StringWriter();
        out.write(UTF8_BOM);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build();
        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (Map<String, Object> row : rows) {
                List<Object> values = new ArrayList<>(CSV_HEADERS.length);
                for (String header : CSV_HEADERS) {
                    values.add(row.get(header));
                }
                printer.printRecord(values);
            }
        } catch (IOException e) {
            // StringWriter không bao giờ ném IOException thật; bọc lại để không phải bắt một
            // ngoại lệ không thể xảy ra ở mọi tầng phía trên.
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /** Toàn bộ chỉ số kèm từng dòng cảnh báo — dạng máy đọc, không cắt bớt gì. */
    @Transactional(readOnly = true)
    public Map<String, Object> toJson(UUID runId, UUID ownerId) {
        RunMetricsResponse metrics = runMetricsService.compute(runId, ownerId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysisRunId", runId);
        payload.put("metrics", metrics);
        payload.put("warnings", warningRows(runId, ownerId));
        return payload;
    }

    /** Tên file gợi ý cho trình duyệt; kèm id run để tải nhiều lần không đè nhau. */
    public String fileName(UUID runId, String extension) {
        return "phan-tich-%s.%s".formatted(runId, extension);
    }

    public byte[] csvBytes(UUID runId, UUID ownerId) {
        return toCsv(runId, ownerId).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Một truy vấn duy nhất cho cả bảng.
     *
     * <p>{@code human_review} gộp bằng {@code MAX}/{@code COUNT} thay vì join thẳng: ở tuần 16
     * nhiều người cùng xét một cảnh báo, và join thẳng sẽ nhân đôi dòng cảnh báo trong file — một
     * lỗi trông y hệt "có nhiều cảnh báo hơn thực tế".
     */
    private List<Map<String, Object>> warningRows(UUID runId, UUID ownerId) {
        runMetricsService.compute(runId, ownerId); // phân quyền + kiểm run truy vấn được
        return jdbcTemplate.queryForList(
                """
                SELECT e.short_id                                        AS short_id,
                       e.file_path                                       AS file_path,
                       e.start_line                                      AS start_line,
                       w.tool                                            AS tool,
                       w.rule_id_normalized                              AS rule_id,
                       w.severity_normalized                             AS severity,
                       agent.verdict                                     AS agent_verdict,
                       agent.downgraded_from_verdict                     AS agent_downgraded_from,
                       COALESCE(agent.claim_count, 0)                    AS claim_count,
                       COALESCE(agent.unsupported_claim_count, 0)        AS unsupported_claim_count,
                       COALESCE(agent.citation_count, 0)                 AS citation_count,
                       COALESCE(agent.citation_resolved_count, 0)        AS citation_resolved_count,
                       hr.decision                                       AS human_decision,
                       hr.used_agent                                     AS human_used_agent,
                       hr.seconds_spent                                  AS human_seconds_spent,
                       hr.note                                           AS human_note,
                       gt.label                                          AS ground_truth_label
                  FROM evidence_record e
                  JOIN warning w ON w.evidence_record_id = e.id
                  LEFT JOIN LATERAL (
                       SELECT m.verdict,
                              m.downgraded_from_verdict,
                              (SELECT count(*) FROM claim cl WHERE cl.message_id = m.id) AS claim_count,
                              (SELECT count(*) FROM claim cl
                                WHERE cl.message_id = m.id
                                  AND NOT EXISTS (SELECT 1 FROM claim_citation cc
                                                   WHERE cc.claim_id = cl.id AND cc.resolution = 'RESOLVED')
                              ) AS unsupported_claim_count,
                              (SELECT count(*) FROM claim_citation cc
                                JOIN claim cl ON cl.id = cc.claim_id WHERE cl.message_id = m.id) AS citation_count,
                              (SELECT count(*) FROM claim_citation cc
                                JOIN claim cl ON cl.id = cc.claim_id
                                WHERE cl.message_id = m.id AND cc.resolution = 'RESOLVED') AS citation_resolved_count
                         FROM message m
                         JOIN conversation c ON c.id = m.conversation_id
                        WHERE c.analysis_run_id = e.analysis_run_id
                          AND c.focus_short_id = e.short_id
                          AND m.role = 'ASSISTANT'
                        ORDER BY m.ordinal DESC
                        LIMIT 1
                  ) agent ON true
                  LEFT JOIN LATERAL (
                       SELECT r.decision,
                              r.message_id IS NOT NULL AS used_agent,
                              r.seconds_spent,
                              r.note
                         FROM human_review r
                        WHERE r.evidence_record_id = e.id
                        ORDER BY r.updated_at DESC
                        LIMIT 1
                  ) hr ON true
                  LEFT JOIN ground_truth_label gt
                         ON gt.evidence_record_id = e.id AND gt.source IN ('MANUAL', 'SEEDED')
                 WHERE e.analysis_run_id = ?
                 ORDER BY w.severity_rank, e.short_id
                """,
                runId);
    }
}
