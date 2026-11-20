package vn.hust.datn.evidence.service.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Đọc {@code per-test-coverage.json} của analyzer sandbox và ghi bảng {@code test_line_coverage}.
 *
 * <p>Chạy SAU materialize (cần evidence_record của từng test, do Surefire sinh) và TRƯỚC linker
 * (linker đọc bảng này). Ghép test theo {@code class + method} với bảng {@code test_case}; test
 * tham số hoá có nhiều lần chạy cùng method được GỘP — cùng một đơn vị trích dẫn.
 *
 * <p><b>Ghép theo TÊN METHOD GỐC, không theo nguyên văn.</b> Hai nguồn ghi tên khác nhau: Surefire
 * (bảng {@code test_case}) thêm chữ ký tham số và chỉ số lần chạy — {@code ghi(Path)},
 * {@code lap(int)[2]} — còn listener ghi {@code MethodSource.getMethodName()}, chỉ có {@code ghi}.
 * So nguyên văn thì MỌI test có tham số (kể cả {@code @TempDir Path}) bị bỏ trong im lặng: gặp thật
 * 20/11/2026, 4/35 test của vulnshop ({@code FileStorageServiceTest}) mất hết độ phủ từng test, và
 * cảnh báo {@code FileStorageService.store:83} hiện ra như "không có test nào" dù một test xanh chạy
 * qua đúng dòng đó. Listener không giữ được danh tính từng lần chạy, nên mọi lần chạy cùng method
 * nhận chung độ phủ đã gộp.
 *
 * <p>Artifact là dữ liệu KHÔNG TIN CẬY (sinh từ repo người dùng): file hỏng thì bỏ qua cả file kèm
 * cảnh báo trong log và linker lùi về theo tên lớp — không làm hỏng lần phân tích, vì đây là phần
 * tăng cường. Đường dẫn không đối chiếu được với snapshot thì bỏ dòng đó, đúng như coverage_line.
 */
@Component
public class PerTestCoverageStore {

    /** Tên file analyzer ghi ra — xem {@code infra/analyzer/java/analyze.sh}. */
    public static final String ARTIFACT_NAME = "per-test-coverage.json";

    private static final String FORMAT = "evidence-per-test-coverage";
    private static final Logger log = LoggerFactory.getLogger(PerTestCoverageStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PerTestCoverageStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** @return số hàng đã ghi; 0 khi file hỏng hoặc không ghép được test nào */
    @Transactional
    public int store(UUID runId, byte[] content, SnapshotPathResolver pathResolver) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (IOException e) {
            log.warn("Run {}: {} khong doc duoc, lien ket theo ten lop: {}", runId, ARTIFACT_NAME, e.getMessage());
            return 0;
        }
        if (root == null || !FORMAT.equals(root.path("format").asText()) || !root.path("tests").isArray()) {
            log.warn("Run {}: {} sai dinh dang, lien ket theo ten lop", runId, ARTIFACT_NAME);
            return 0;
        }

        Map<String, List<UUID>> testIds = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT tc.evidence_record_id, tc.class_name, tc.method_name
                FROM test_case tc JOIN evidence_record e ON e.id = tc.evidence_record_id
                WHERE e.analysis_run_id = ?
                """,
                rs -> {
                    testIds.computeIfAbsent(
                                    testKey(rs.getString("class_name"), rs.getString("method_name")),
                                    key -> new ArrayList<>())
                            .add(rs.getObject("evidence_record_id", UUID.class));
                },
                runId);

        Map<String, String> resolvedPaths = new HashMap<>();
        List<Object[]> rows = new ArrayList<>();
        int unmatchedTests = 0;
        for (JsonNode test : root.path("tests")) {
            List<UUID> ids = testIds.get(testKey(test.path("class").asText(), test.path("method").asText()));
            if (ids == null) {
                unmatchedTests++;
                continue;
            }
            test.path("lines").properties().forEach(file -> {
                String filePath = resolvedPaths.computeIfAbsent(file.getKey(), raw -> {
                    ResolvedPath resolved = pathResolver.resolve(raw);
                    return resolved.resolved() ? resolved.filePath() : "";
                });
                if (filePath.isEmpty() || !file.getValue().isArray()) {
                    return;
                }
                for (JsonNode line : file.getValue()) {
                    if (line.canConvertToInt() && line.asInt() > 0) {
                        for (UUID testId : ids) {
                            rows.add(new Object[] {testId, filePath, line.asInt()});
                        }
                    }
                }
            });
        }

        // ON CONFLICT DO NOTHING: test tham số hoá chạy nhiều lần cùng method ⇒ trùng (test, file, dòng).
        jdbcTemplate.batchUpdate(
                "INSERT INTO test_line_coverage (test_evidence_id, file_path, line_number) VALUES (?, ?, ?)"
                        + " ON CONFLICT DO NOTHING",
                rows);
        if (unmatchedTests > 0) {
            // Không nuốt: tên test lệch giữa Surefire và listener thì bằng chứng mất trong im lặng.
            log.warn("Run {}: {} test trong {} khong ghep duoc voi test_case", runId, unmatchedTests, ARTIFACT_NAME);
        }
        return rows.size();
    }

    /** Khoá ghép {@code lớp#method_gốc} — xem Javadoc của lớp. */
    static String testKey(String className, String methodName) {
        return className + "#" + baseMethodName(methodName);
    }

    /**
     * Tên method bỏ phần Surefire thêm vào: chữ ký tham số {@code (…)} và chỉ số lần chạy {@code […]}.
     * {@code ghi(Path)} → {@code ghi}; {@code lap(int)[2]} → {@code lap}; {@code ghi} giữ nguyên.
     */
    static String baseMethodName(String methodName) {
        if (methodName == null) {
            return "";
        }
        int cut = methodName.length();
        int paren = methodName.indexOf('(');
        int bracket = methodName.indexOf('[');
        if (paren >= 0) {
            cut = paren;
        }
        if (bracket >= 0 && bracket < cut) {
            cut = bracket;
        }
        return methodName.substring(0, cut).strip();
    }
}
