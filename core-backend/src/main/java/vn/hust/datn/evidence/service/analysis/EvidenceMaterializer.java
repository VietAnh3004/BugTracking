package vn.hust.datn.evidence.service.analysis;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.StackFrameKind;

/**
 * Biến danh sách {@link EvidenceDraft} thuần hàm thành các hàng thật trong Evidence Store.
 *
 * <h2>Đây là nơi DUY NHẤT cấp {@code seq_in_run} và là nơi DUY NHẤT ghi evidence</h2>
 *
 * Parser tuyệt đối không có repository nào trong constructor. Ranh giới đó không phải để cho đẹp:
 * nó là điều kiện để parser unit-test được bằng đúng artifact trong {@code docs/samples/}, và là
 * điều kiện để {@code seq_in_run} tăng liên tục không lỗ hổng trong phạm vi một run (ràng buộc
 * {@code UNIQUE (analysis_run_id, seq_in_run)} và công thức sinh {@code short_id} dựa vào nó).
 *
 * <h2>Vì sao ghi bằng JdbcTemplate chứ không phải JPA — bất đối xứng CÓ CHỦ ĐÍCH</h2>
 *
 * {@code EvidenceRecord.shortId} mang {@code @Generated(event = INSERT)} vì cột đó là
 * {@code GENERATED ALWAYS AS (...) STORED} phía Postgres. Hibernate buộc phải ĐỌC LẠI hàng vừa
 * chèn để lấy giá trị ấy, nên nó <b>vô hiệu hoá JDBC batching cho chính bảng cha của mọi thứ</b>:
 * 90 evidence của vulnshop thành 90 lượt INSERT + 90 lượt SELECT, và ở repo lớn thì con số này
 * nhân lên tuyến tính.
 *
 * <p>Entity JPA vẫn viết đủ và vẫn đúng — chúng phục vụ <b>đường đọc</b> (query service, tool RPC)
 * và để {@code ddl-auto: validate} canh lệch schema. Chỉ riêng đường GHI của materialize đi bằng
 * JDBC thuần.
 *
 * <h2>Vì sao toàn bộ nằm trong MỘT transaction</h2>
 *
 * Khác {@code SnapshotService} (ở đó cấm bọc transaction quanh vòng lặp vì có hàng nghìn lượt gọi
 * mạng tới MinIO bên trong). Ở đây không có I/O ngoài nào: artifact đã nằm trong {@code byte[]},
 * chỉ mục đường dẫn đã nạp trước vào {@link SnapshotPathResolver}. Một run materialize dở dang —
 * có warning, mất coverage — tệ hơn nhiều so với không có run nào, vì nó <b>trông y hệt một run
 * hợp lệ</b>.
 */
@Service
public class EvidenceMaterializer {

    /**
     * Chia nhỏ batch: một repo lớn có thể cho hàng trăm nghìn {@code coverage_line}, và một lệnh
     * batch duy nhất cỡ đó giữ toàn bộ tham số trong bộ nhớ của cả driver lẫn server.
     */
    private static final int BATCH_CHUNK = 1000;

    private static final String INSERT_EVIDENCE =
            """
            INSERT INTO evidence_record
                (id, seq_in_run, analysis_run_id, type, source, file_path, start_line, end_line,
                 symbol, summary, content, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?)
            """;

    // ?::jsonb là BẮT BUỘC: thiếu ép kiểu, Postgres báo "column content is of type jsonb but
    // expression is of type character varying" — lỗi chỉ lộ ra lúc chạy, không lúc biên dịch.
    private static final int[] EVIDENCE_ARG_TYPES = {
        Types.OTHER,
        Types.INTEGER,
        Types.OTHER,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.INTEGER,
        Types.INTEGER,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.TIMESTAMP_WITH_TIMEZONE
    };

    private static final String INSERT_WARNING =
            """
            INSERT INTO warning
                (evidence_record_id, tool, rule_id, rule_id_normalized, severity_raw,
                 severity_normalized, cwe_ids, has_primary_location, source_index)
            VALUES (?, ?, ?, ?, ?, ?, ?::int[], ?, ?)
            """;

    private static final int[] WARNING_ARG_TYPES = {
        Types.OTHER,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.BOOLEAN,
        Types.INTEGER
    };

    private static final String INSERT_WARNING_LOCATION =
            """
            INSERT INTO warning_location
                (evidence_record_id, ordinal, role_raw, is_primary, path_raw, file_path,
                 start_line, end_line, class_name, method_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final int[] WARNING_LOCATION_ARG_TYPES = {
        Types.OTHER,
        Types.INTEGER,
        Types.VARCHAR,
        Types.BOOLEAN,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.INTEGER,
        Types.INTEGER,
        Types.VARCHAR,
        Types.VARCHAR
    };

    private static final String INSERT_TEST_CASE =
            """
            INSERT INTO test_case
                (evidence_record_id, class_name, method_name, display_name, status, failure_kind,
                 failure_type, failure_message, stack_trace_raw, duration_ms,
                 stack_trace_evidence_id, report_file)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final int[] TEST_CASE_ARG_TYPES = {
        Types.OTHER,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.INTEGER,
        Types.OTHER,
        Types.VARCHAR
    };

    private static final String INSERT_STACK_FRAME =
            """
            INSERT INTO stack_frame
                (evidence_record_id, ordinal, raw_line, module_prefix, declaring_class,
                 method_name, source_file_name, line_number, file_path, frame_kind)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final int[] STACK_FRAME_ARG_TYPES = {
        Types.OTHER,
        Types.INTEGER,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.INTEGER,
        Types.VARCHAR,
        Types.VARCHAR
    };

    private static final String INSERT_COVERAGE_LINE =
            """
            INSERT INTO coverage_line (evidence_record_id, line_number, mi, ci, mb, cb)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final int[] COVERAGE_LINE_ARG_TYPES = {
        Types.OTHER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER
    };

    private static final String INSERT_COVERAGE_METHOD =
            """
            INSERT INTO coverage_method
                (id, evidence_record_id, class_name, method_name, method_desc, first_line,
                 missed_instructions, covered_instructions, missed_branches, covered_branches,
                 missed_lines, covered_lines)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final int[] COVERAGE_METHOD_ARG_TYPES = {
        Types.OTHER,
        Types.OTHER,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.VARCHAR,
        Types.INTEGER,
        Types.INTEGER,
        Types.INTEGER,
        Types.INTEGER,
        Types.INTEGER,
        Types.INTEGER,
        Types.INTEGER
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EvidenceMaterializer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @param drafts thứ tự của danh sách này TRỞ THÀNH {@code seq_in_run} — tầng điều phối phải
     *     nạp artifact theo thứ tự cố định, nếu không cùng một snapshot cho ra hai bộ Evidence ID
     *     khác nhau giữa hai lần chạy và tính tái lập ở tuần 15-16 mất căn cứ
     */
    @Transactional
    public MaterializationResult materialize(UUID runId, List<EvidenceDraft> drafts, SnapshotPathResolver pathResolver) {
        OffsetDateTime now = OffsetDateTime.now();

        // Lượt 1: cấp id cho TOÀN BỘ draft trước, vì test_case.stack_trace_evidence_id trỏ tới id
        // của một evidence khác trong cùng danh sách. Parser cố ý không sinh UUID (sẽ thành thứ
        // có trạng thái, hết unit-test thuần được) nên việc nối lại nằm ở đây.
        List<UUID> ids = new ArrayList<>(drafts.size());
        Map<String, UUID> stackTraceIdByCorrelation = new HashMap<>();
        for (EvidenceDraft draft : drafts) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            if (draft instanceof StackTraceDraft trace && trace.traceCorrelationId() != null) {
                stackTraceIdByCorrelation.put(trace.traceCorrelationId(), id);
            }
        }

        Rows rows = new Rows();
        Map<EvidenceType, Integer> countByType = new EnumMap<>(EvidenceType.class);

        for (int index = 0; index < drafts.size(); index++) {
            EvidenceDraft draft = drafts.get(index);
            UUID id = ids.get(index);
            countByType.merge(draft.type(), 1, Integer::sum);

            // Switch vét cạn trên sealed interface: thêm một loại bằng chứng mà quên xử lý là lỗi
            // BIÊN DỊCH, không phải một nhánh bị bỏ quên trong im lặng.
            ResolvedPath location =
                    switch (draft) {
                        case WarningDraft warning -> prepareWarning(rows, id, warning, pathResolver);
                        case TestCaseDraft testCase -> prepareTestCase(
                                rows, id, testCase, pathResolver, stackTraceIdByCorrelation);
                        case StackTraceDraft trace -> prepareStackTrace(rows, id, trace, pathResolver);
                        case CoverageDraft coverage -> prepareCoverage(rows, id, coverage, pathResolver);
                    };

            rows.evidence.add(new Object[] {
                id,
                index + 1,
                runId,
                draft.type().name(),
                draft.source().name(),
                location == null ? null : location.filePath(),
                draft.startLine(),
                draft.endLine(),
                draft.symbol(),
                draft.summary(),
                toJson(contentWithMatchKind(draft.content(), location)),
                now
            });
        }

        // Thứ tự bắt buộc theo FK: evidence_record trước, mọi bảng mở rộng sau. warning_location
        // trỏ tới warning (không phải evidence_record) nên phải sau cả warning.
        batchInsert(INSERT_EVIDENCE, EVIDENCE_ARG_TYPES, rows.evidence);
        batchInsert(INSERT_WARNING, WARNING_ARG_TYPES, rows.warnings);
        batchInsert(INSERT_WARNING_LOCATION, WARNING_LOCATION_ARG_TYPES, rows.warningLocations);
        batchInsert(INSERT_TEST_CASE, TEST_CASE_ARG_TYPES, rows.testCases);
        batchInsert(INSERT_STACK_FRAME, STACK_FRAME_ARG_TYPES, rows.stackFrames);
        batchInsert(INSERT_COVERAGE_LINE, COVERAGE_LINE_ARG_TYPES, rows.coverageLines);
        batchInsert(INSERT_COVERAGE_METHOD, COVERAGE_METHOD_ARG_TYPES, rows.coverageMethods);

        return new MaterializationResult(
                drafts.size(),
                countByType.getOrDefault(EvidenceType.STATIC_WARNING, 0),
                rows.locationCount,
                rows.unresolvedLocationCount,
                Map.copyOf(countByType));
    }

    private ResolvedPath prepareWarning(Rows rows, UUID id, WarningDraft draft, SnapshotPathResolver pathResolver) {
        rows.warnings.add(new Object[] {
            id,
            draft.tool().name(),
            draft.ruleId(),
            draft.ruleIdNormalized(),
            draft.severityRaw(),
            draft.severityNormalized().name(),
            toPostgresIntArray(draft.cweIds()),
            draft.hasPrimaryLocation(),
            draft.sourceIndex()
        });

        ResolvedPath primary = null;
        for (WarningLocationDraft location : draft.locations()) {
            ResolvedPath resolved = rows.count(pathResolver.resolve(location.pathRaw()));
            if (primary == null || location.primary()) {
                primary = resolved;
            }
            rows.warningLocations.add(new Object[] {
                id,
                location.ordinal(),
                location.roleRaw(),
                location.primary(),
                location.pathRaw(),
                resolved.filePath(),
                location.startLine(),
                location.endLine(),
                location.className(),
                location.methodName()
            });
        }
        // file_path của evidence_record lấy từ vị trí chính đã đối chiếu ở trên — KHÔNG resolve
        // lại draft.rawFilePath(), nếu không mỗi cảnh báo bị đếm hai lần vào location_count.
        return primary;
    }

    private ResolvedPath prepareTestCase(
            Rows rows,
            UUID id,
            TestCaseDraft draft,
            SnapshotPathResolver pathResolver,
            Map<String, UUID> stackTraceIdByCorrelation) {
        rows.testCases.add(new Object[] {
            id,
            draft.className(),
            draft.methodName(),
            draft.displayName(),
            draft.status().name(),
            draft.failureKind() == null ? null : draft.failureKind().name(),
            draft.failureType(),
            draft.failureMessage(),
            draft.stackTraceRaw(),
            draft.durationMs(),
            draft.traceCorrelationId() == null ? null : stackTraceIdByCorrelation.get(draft.traceCorrelationId()),
            draft.reportFile()
        });
        return rows.count(pathResolver.resolve(draft.rawFilePath()));
    }

    private ResolvedPath prepareStackTrace(Rows rows, UUID id, StackTraceDraft draft, SnapshotPathResolver pathResolver) {
        ResolvedPath firstProduction = null;
        for (StackFrameDraft frame : draft.frames()) {
            ResolvedPath resolved = rows.count(resolveFrame(frame, pathResolver));
            if (firstProduction == null && frame.frameKind() == StackFrameKind.PRODUCTION) {
                firstProduction = resolved;
            }
            rows.stackFrames.add(new Object[] {
                id,
                frame.ordinal(),
                frame.rawLine(),
                frame.modulePrefix(),
                frame.declaringClass(),
                frame.methodName(),
                frame.sourceFileName(),
                frame.lineNumber(),
                resolved.filePath(),
                frame.frameKind().name()
            });
        }
        // 3/5 test hỏng thật không có frame production nào — khi đó evidence STACK_TRACE không có
        // vị trí, và đó là dữ liệu đúng chứ không phải thất bại đối chiếu.
        return firstProduction;
    }

    private ResolvedPath prepareCoverage(Rows rows, UUID id, CoverageDraft draft, SnapshotPathResolver pathResolver) {
        for (CoverageLineDraft line : draft.lines()) {
            // KHÔNG ghi cột status: Postgres tự sinh từ mi/ci/mb/cb. Ghi từ tầng ứng dụng là tạo
            // nguồn sự thật thứ hai cho cùng một công thức.
            rows.coverageLines.add(
                    new Object[] {id, line.lineNumber(), line.mi(), line.ci(), line.mb(), line.cb()});
        }
        for (CoverageMethodDraft method : draft.methods()) {
            rows.coverageMethods.add(new Object[] {
                UUID.randomUUID(),
                id,
                method.className(),
                method.methodName(),
                method.methodDesc(),
                method.firstLine(),
                method.missedInstructions(),
                method.coveredInstructions(),
                method.missedBranches(),
                method.coveredBranches(),
                method.missedLines(),
                method.coveredLines()
            });
        }
        return rows.count(pathResolver.resolve(draft.rawFilePath()));
    }

    /**
     * Frame JDK/JUnit/thư viện KHÔNG bao giờ nằm trong snapshot, nên không được tính là thất bại
     * đối chiếu. Tính chúng thì tỉ lệ hỏng của một run hoàn toàn đúng vẫn là 37/44 ≈ 84% và bộ
     * đếm mất sạch giá trị cảnh báo.
     */
    private ResolvedPath resolveFrame(StackFrameDraft frame, SnapshotPathResolver pathResolver) {
        String rawPath = frame.rawFilePath();
        if (rawPath == null) {
            // '(Native Method)' / '(Unknown Source)': KHÔNG CÓ tên file để mà đối chiếu.
            return ResolvedPath.notApplicable(null);
        }
        return switch (frame.frameKind()) {
            case PRODUCTION, TEST -> pathResolver.resolve(rawPath);
            case TEST_FRAMEWORK, JDK, LIBRARY, UNKNOWN -> ResolvedPath.notApplicable(rawPath);
        };
    }

    /**
     * Gắn {@code path_match_kind} vào {@code content}. Cố ý không lên cột DB riêng (xem
     * {@link PathMatchKind}), nhưng vẫn phải lưu được để tuần 15 đo "bao nhiêu phần trăm vị trí
     * chỉ khớp bằng tên file".
     */
    private Map<String, Object> contentWithMatchKind(Map<String, Object> content, ResolvedPath location) {
        Map<String, Object> enriched = new LinkedHashMap<>(content == null ? Map.of() : content);
        if (location != null) {
            enriched.put("path_match_kind", location.kind().name());
        }
        return enriched;
    }

    /** {@code {328,327}} — literal mảng Postgres, ép kiểu bằng {@code ?::int[]} trong câu lệnh. */
    private String toPostgresIntArray(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            // NULL chứ không phải '{}': "không có CWE nào" (8/28 BugInstance thật) khác hẳn "có
            // mảng rỗng", và cột này nullable chính vì lý do đó.
            return null;
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private String toJson(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Khong serialize duoc content cua evidence sang JSON", e);
        }
    }

    private void batchInsert(String sql, int[] argTypes, List<Object[]> batchArgs) {
        for (int from = 0; from < batchArgs.size(); from += BATCH_CHUNK) {
            int to = Math.min(from + BATCH_CHUNK, batchArgs.size());
            jdbcTemplate.batchUpdate(sql, batchArgs.subList(from, to), argTypes);
        }
    }

    /** Bộ tích luỹ hàng của một lần materialize; sống đúng trong phạm vi một lời gọi. */
    private static final class Rows {
        private final List<Object[]> evidence = new ArrayList<>();
        private final List<Object[]> warnings = new ArrayList<>();
        private final List<Object[]> warningLocations = new ArrayList<>();
        private final List<Object[]> testCases = new ArrayList<>();
        private final List<Object[]> stackFrames = new ArrayList<>();
        private final List<Object[]> coverageLines = new ArrayList<>();
        private final List<Object[]> coverageMethods = new ArrayList<>();

        private int locationCount;
        private int unresolvedLocationCount;

        /** Mỗi vị trí chỉ đi qua đây ĐÚNG MỘT LẦN — đếm hai lần làm hỏng mẫu số của tỉ lệ. */
        private ResolvedPath count(ResolvedPath resolved) {
            if (resolved.counted()) {
                locationCount++;
                if (!resolved.resolved()) {
                    unresolvedLocationCount++;
                }
            }
            return resolved;
        }
    }
}
