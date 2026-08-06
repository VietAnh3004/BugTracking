package vn.hust.datn.evidence.service.linker;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;
import vn.hust.datn.evidence.repository.StackFrameRepository;
import vn.hust.datn.evidence.repository.TestCaseRepository;
import vn.hust.datn.evidence.repository.WarningLocationRepository;

/**
 * Nối bằng chứng rời rạc thành chuỗi bằng chứng — phần lõi của tuần 10-11.
 *
 * <h2>Chạy lại được, và đó là yêu cầu chứ không phải tiện nghi</h2>
 *
 * Linker đọc TỪ DB chứ không từ draft trong bộ nhớ, và xoá sạch liên kết của run trước khi ghi
 * lại. Lý do: heuristic ở đây (khớp dòng, quy ước tên, phân loại frame) chắc chắn phải sửa, và
 * sửa xong mà phải chạy lại cả sandbox thì rất tốn.
 *
 * <p><b>Ranh giới của việc chạy lại</b>: linker tính lại được CÁCH KHỚP, nhưng không tính lại được
 * KẾT QUẢ PARSE. Sửa {@code CodeContextExtractor}, {@code StackTraceParser} hay chuẩn hoá đường dẫn
 * thì phải phân tích lại từ đầu — vì chúng sinh ra {@code evidence_record}, mà {@code short_id} của
 * evidence là thứ LLM đã trích dẫn và không được phép đổi.
 *
 * <h2>Không có trạng thái trung gian quan sát được</h2>
 *
 * Ở đường ingest, linker chạy sau {@code materialize} nhưng TRƯỚC {@code finalizeRun}, tức lúc run
 * vẫn {@code RUNNING} — mà {@code EvidenceQueryService.ensureRunQueryable} từ chối {@code RUNNING}.
 * Nên "có evidence, chưa có liên kết" không bao giờ lộ ra qua API, và điều đó đạt được bằng TRẠNG
 * THÁI RUN chứ không phải bằng transaction bao ngoài — {@code AnalysisRunService} cố ý không mang
 * {@code @Transactional}.
 */
@Service
public class EvidenceLinker {

    private static final Logger log = LoggerFactory.getLogger(EvidenceLinker.class);

    private static final String DELETE_LINKS = "DELETE FROM evidence_link WHERE analysis_run_id = ?";

    // KHÔNG có ON CONFLICT: sau LinkAccumulator thì trùng nghĩa là bug dedup, và bug đó phải nổ.
    private static final String INSERT_LINK =
            """
            INSERT INTO evidence_link
                (id, analysis_run_id, from_evidence_id, to_evidence_id, relation, weight, method, rationale, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final int[] INSERT_ARG_TYPES = {
        Types.OTHER, Types.OTHER, Types.OTHER, Types.OTHER, Types.VARCHAR,
        Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE
    };

    private static final int BATCH_CHUNK = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final WarningLocationRepository warningLocationRepository;
    private final StackFrameRepository stackFrameRepository;
    private final TestCaseRepository testCaseRepository;

    public EvidenceLinker(
            JdbcTemplate jdbcTemplate,
            EvidenceRecordRepository evidenceRecordRepository,
            WarningLocationRepository warningLocationRepository,
            StackFrameRepository stackFrameRepository,
            TestCaseRepository testCaseRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.warningLocationRepository = warningLocationRepository;
        this.stackFrameRepository = stackFrameRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional
    public LinkResult link(UUID runId) {
        int deleted = jdbcTemplate.update(DELETE_LINKS, runId);

        List<WarningLocationRow> locations = warningLocationRepository.findResolvedRowsByRun(runId);
        List<EvidenceRecord> contexts = evidenceRecordRepository
                .findByAnalysisRunIdAndType(runId, EvidenceType.CODE_CONTEXT, Pageable.unpaged())
                .getContent();

        List<StackFrameRow> frames = stackFrameRepository.findProductionFramesByRun(runId);
        List<TestCaseRow> testCases = testCaseRepository.findRowsByRun(runId);

        LinkAccumulator accumulator = new LinkAccumulator();
        accumulator.addAll(locatedIn(locations, contexts));
        accumulator.addAll(supportsByStackFrame(locations, frames, testCases));

        List<LinkCandidate> candidates = accumulator.candidates();
        write(runId, candidates);

        // Gom CẢ HAI đầu: tầng 1 sinh warning → context (cảnh báo ở đầu `from`), tầng 3 sinh
        // test → warning (cảnh báo ở đầu `to`). Chỉ đếm một đầu thì mọi cảnh báo chỉ có bằng
        // chứng từ test sẽ bị tính nhầm là "trơ trọi".
        Set<UUID> linkedWarnings = candidates.stream()
                .flatMap(candidate -> java.util.stream.Stream.of(candidate.fromEvidenceId(), candidate.toEvidenceId()))
                .collect(Collectors.toSet());
        int unlinked = (int) locations.stream()
                .map(WarningLocationRow::evidenceRecordId)
                .distinct()
                .filter(id -> !linkedWarnings.contains(id))
                .count();

        Map<String, Object> countByMethod = candidates.stream()
                .collect(Collectors.groupingBy(
                        candidate -> candidate.method().name(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        log.info(
                "Run {}: {} lien ket ({} canh bao khong co lien ket nao), xoa {} lien ket cu, phan bo {}",
                runId,
                candidates.size(),
                unlinked,
                deleted,
                countByMethod);

        return new LinkResult(candidates.size(), unlinked, deleted, Map.copyOf(countByMethod));
    }

    /**
     * Tầng 1 — {@code LOCATED_IN}: cảnh báo NẰM TRONG đoạn mã nào.
     *
     * <p>Đây là tầng bắt buộc và làm trước, đúng như kế hoạch §3: nó là khoá nối duy nhất mà MVP
     * cần, và hai tầng còn lại đều dựa vào việc biết cảnh báo thuộc method nào.
     *
     * <p>Hướng: {@code warning → code_context}, đọc là "cảnh báo này nằm trong đoạn mã này".
     */
    private List<LinkCandidate> locatedIn(List<WarningLocationRow> locations, List<EvidenceRecord> contexts) {
        Map<String, List<EvidenceRecord>> contextsByFile = contexts.stream()
                .filter(context -> context.getFilePath() != null && context.getStartLine() != null)
                .collect(Collectors.groupingBy(EvidenceRecord::getFilePath));

        List<LinkCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (WarningLocationRow location : locations) {
            List<EvidenceRecord> inFile = contextsByFile.getOrDefault(location.filePath(), List.of());
            if (inFile.isEmpty()) {
                continue;
            }
            int line = location.startLine();

            // HẸP NHẤT thắng: một dòng nằm trong range của cả method lẫn class bao ngoài, mà chỉ
            // method mới là "ngữ cảnh" đúng nghĩa. Extractor đã chọn hẹp nhất khi SINH context,
            // nhưng một file có thể có nhiều context chồng nhau nếu cảnh báo rơi vào nhiều mức.
            Optional<EvidenceRecord> narrowest = inFile.stream()
                    .filter(context -> containsLine(context, line))
                    .min(Comparator.comparingInt(EvidenceLinker::heightOf));

            if (narrowest.isEmpty()) {
                continue;
            }
            EvidenceRecord context = narrowest.get();
            boolean exact = location.endLine() == null || location.endLine().equals(location.startLine());
            EvidenceLinkMethod method = exact ? EvidenceLinkMethod.LINE_EXACT : EvidenceLinkMethod.RANGE_OVERLAP;

            LinkCandidate candidate = new LinkCandidate(
                    location.evidenceRecordId(),
                    context.getId(),
                    EvidenceLinkRelation.LOCATED_IN,
                    method,
                    exact ? 1.0 : 0.8,
                    "Dong %d cua canh bao nam trong %s (dong %d-%d)"
                            .formatted(
                                    line,
                                    context.getSymbol() != null ? context.getSymbol() : context.getFilePath(),
                                    context.getStartLine(),
                                    context.getEndLine()));
            if (seen.add(candidate.dedupeKey())) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Tầng 3 — {@code SUPPORTS} theo stack frame: test hỏng NÀO đi qua đúng dòng bị cảnh báo.
     *
     * <p>Đây là loại bằng chứng thuyết phục nhất cho verdict "Likely True Positive": không phải
     * suy diễn theo tên hay theo độ phủ tổng hợp, mà là một lần chạy thật đã đi qua đúng dòng đó
     * rồi ném lỗi.
     *
     * <p><b>Hướng: {@code failed_test → warning}</b>, không phải {@code stack_trace → warning}.
     * Liên kết phải trỏ về evidence mà người dùng gọi là "test liên quan"; nếu trỏ vào evidence
     * {@code STACK_TRACE} thì giao diện hiện ra một stack trace thay vì một cái tên test. Quy đổi
     * bằng {@code test_case.stack_trace_evidence_id} — provenance cấu trúc đã có sẵn từ tuần 8-9,
     * không cần thêm liên kết ngữ nghĩa nào.
     *
     * <p><b>Tầng này KHÔNG đủ một mình.</b> Trên vulnshop chỉ 7/44 frame trỏ về production, và
     * 3/5 test hỏng không có frame production nào — toàn bộ test hỏng kiểu {@code AssertionFailedError}
     * có trace chỉ chứa {@code org.junit.jupiter.api.*} rồi tới lớp test, không hề đi qua mã đang
     * kiểm thử. Đó chính là lý do tầng 2 (độ phủ + quy ước tên) tồn tại.
     */
    private List<LinkCandidate> supportsByStackFrame(
            List<WarningLocationRow> locations, List<StackFrameRow> frames, List<TestCaseRow> testCases) {
        if (frames.isEmpty() || locations.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> testByStackTrace = testCases.stream()
                .filter(testCase -> testCase.stackTraceEvidenceId() != null)
                .collect(Collectors.toMap(
                        TestCaseRow::stackTraceEvidenceId, TestCaseRow::evidenceRecordId, (first, second) -> first));

        Map<String, List<WarningLocationRow>> locationsByFile =
                locations.stream().collect(Collectors.groupingBy(WarningLocationRow::filePath));

        List<LinkCandidate> candidates = new ArrayList<>();
        for (StackFrameRow frame : frames) {
            UUID testEvidenceId = testByStackTrace.get(frame.stackTraceEvidenceId());
            if (testEvidenceId == null) {
                // Stack trace không quy được về test nào. Không im lặng bỏ qua — nhưng cũng không
                // dựng liên kết trỏ vào một evidence mà UI không biết trình bày.
                log.debug("Stack trace {} khong co test_case nao tro toi", frame.stackTraceEvidenceId());
                continue;
            }
            for (WarningLocationRow location : locationsByFile.getOrDefault(frame.filePath(), List.of())) {
                int warningStart = location.startLine();
                int warningEnd = location.endLine() != null ? location.endLine() : warningStart;
                int frameLine = frame.lineNumber();

                boolean exact = frameLine == warningStart;
                boolean withinRange = warningStart <= frameLine && frameLine <= warningEnd;
                if (!exact && !withinRange) {
                    continue;
                }
                candidates.add(new LinkCandidate(
                        testEvidenceId,
                        location.evidenceRecordId(),
                        EvidenceLinkRelation.SUPPORTS,
                        exact ? EvidenceLinkMethod.STACK_FRAME : EvidenceLinkMethod.RANGE_OVERLAP,
                        exact ? 1.0 : 0.7,
                        "Stack trace cua test hong di qua dong %d: %s".formatted(frameLine, frame.rawLine())));
            }
        }
        return candidates;
    }

    private void write(UUID runId, List<LinkCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Object[]> rows = candidates.stream()
                .map(candidate -> new Object[] {
                    UUID.randomUUID(),
                    runId,
                    candidate.fromEvidenceId(),
                    candidate.toEvidenceId(),
                    candidate.relation().name(),
                    candidate.weight(),
                    candidate.method().name(),
                    candidate.rationale(),
                    now
                })
                .toList();

        for (int from = 0; from < rows.size(); from += BATCH_CHUNK) {
            jdbcTemplate.batchUpdate(
                    INSERT_LINK, rows.subList(from, Math.min(from + BATCH_CHUNK, rows.size())), INSERT_ARG_TYPES);
        }
    }

    private static boolean containsLine(EvidenceRecord context, int line) {
        int start = context.getStartLine();
        int end = context.getEndLine() != null ? context.getEndLine() : start;
        return start <= line && line <= end;
    }

    private static int heightOf(EvidenceRecord context) {
        int start = context.getStartLine();
        int end = context.getEndLine() != null ? context.getEndLine() : start;
        return end - start;
    }
}
