package vn.hust.datn.evidence.service.linker;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.exception.LinkerProducedNothingException;
import vn.hust.datn.evidence.repository.CoverageLineRepository;
import vn.hust.datn.evidence.repository.CoverageMethodRepository;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Bán kính tìm dòng executable gần nhất khi chính dòng cảnh báo không có hàng độ phủ.
     *
     * <p>Hẹp có chủ đích: nới rộng ra thì "vùng gần đó có chạy" mất hết ý nghĩa, mà liên kết vẫn
     * cứ sinh ra — tức là đổi một khoảng trống thành một bằng chứng giả.
     */
    private static final int NEAREST_LINE_RADIUS = 3;

    private final JdbcTemplate jdbcTemplate;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final WarningLocationRepository warningLocationRepository;
    private final StackFrameRepository stackFrameRepository;
    private final TestCaseRepository testCaseRepository;
    private final CoverageLineRepository coverageLineRepository;
    private final CoverageMethodRepository coverageMethodRepository;

    public EvidenceLinker(
            JdbcTemplate jdbcTemplate,
            EvidenceRecordRepository evidenceRecordRepository,
            WarningLocationRepository warningLocationRepository,
            StackFrameRepository stackFrameRepository,
            TestCaseRepository testCaseRepository,
            CoverageLineRepository coverageLineRepository,
            CoverageMethodRepository coverageMethodRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.warningLocationRepository = warningLocationRepository;
        this.stackFrameRepository = stackFrameRepository;
        this.testCaseRepository = testCaseRepository;
        this.coverageLineRepository = coverageLineRepository;
        this.coverageMethodRepository = coverageMethodRepository;
    }

    /** Chia khối để không vượt trần 65535 bind parameter của Postgres trên repo lớn. */
    private List<CoverageLineRow> readCoverageLines(UUID runId, Set<String> filePaths) {
        if (filePaths.isEmpty()) {
            return List.of();
        }
        List<String> paths = List.copyOf(filePaths);
        List<CoverageLineRow> all = new ArrayList<>();
        for (int from = 0; from < paths.size(); from += BATCH_CHUNK) {
            all.addAll(coverageLineRepository.findByRunAndFilePaths(
                    runId, paths.subList(from, Math.min(from + BATCH_CHUNK, paths.size()))));
        }
        return all;
    }

    /**
     * Chạy lại toàn bộ liên kết cho một run ĐÃ XONG, rồi tự ghi bộ đếm.
     *
     * <p>Khác {@link #link(UUID)} ở hai chỗ, và cả hai đều bắt buộc:
     *
     * <ul>
     *   <li><b>Tự UPDATE {@code analysis_run}</b>. Ở đường ingest thì không được phép — bộ đếm
     *       phải đi qua {@code RunOutcome} vì {@code finalizeRun} là {@code REQUIRES_NEW} và sẽ
     *       ghi đè. Ở đây không có {@code finalizeRun} nào chạy sau, nên tự ghi là đúng và là cách
     *       duy nhất.
     *   <li><b>Khoá hàng run bằng {@code FOR UPDATE}</b>. Hai lần rebuild song song sẽ cùng xoá
     *       rồi cùng chèn, và cái chạy sau đâm vào {@code UNIQUE (from, to, relation)} — một lỗi
     *       500 khó hiểu cho một thao tác lẽ ra idempotent.
     * </ul>
     *
     * <p>Toàn bộ nằm trong MỘT transaction: ra 0 liên kết trong khi có ngữ cảnh mã nguồn thì ném,
     * transaction rollback, và liên kết cũ còn nguyên.
     */
    @Transactional
    public LinkResult relink(UUID runId) {
        Integer codeContextCount = jdbcTemplate.queryForObject(
                "SELECT code_context_count FROM analysis_run WHERE id = ? FOR UPDATE", Integer.class, runId);

        LinkResult result = link(runId);

        if (result.linkCount() == 0 && codeContextCount != null && codeContextCount > 0) {
            throw new LinkerProducedNothingException(runId, codeContextCount);
        }

        jdbcTemplate.update(
                "UPDATE analysis_run SET link_count = ?, unlinked_warning_count = ?, link_counts_by_method = ?::jsonb "
                        + "WHERE id = ?",
                result.linkCount(),
                result.unlinkedWarningCount(),
                toJson(result.countByMethod()),
                runId);
        return result;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Khong serialize duoc phan bo lien ket", e);
        }
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

        // Chỉ nạp độ phủ của những FILE CÓ CẢNH BÁO — xem Javadoc CoverageLineRepository.
        Set<String> filesWithWarnings =
                locations.stream().map(WarningLocationRow::filePath).collect(Collectors.toCollection(LinkedHashSet::new));
        List<CoverageLineRow> coverageLines = readCoverageLines(runId, filesWithWarnings);
        List<CoverageMethodRow> coverageMethods = coverageMethodRepository.findRowsByRun(runId);

        LinkAccumulator accumulator = new LinkAccumulator();
        accumulator.addAll(locatedIn(locations, contexts));
        accumulator.addAll(supportsByStackFrame(locations, frames, testCases));
        accumulator.addAll(covers(locations, coverageLines));

        // Pass cuối CẦN biết những tầng trước đã sinh SUPPORTS cho cảnh báo nào, nên nó chạy trên
        // KẾT QUẢ ĐÃ DEDUP chứ không song song với các tầng.
        List<LinkCandidate> soFar = accumulator.candidates();
        accumulator.addAll(byTestNaming(locations, contexts, testCases, coverageMethods, coverageLines, soFar));

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

        // Phân bố theo QUAN HỆ đi kèm phân bố theo phương pháp: cái sau nói "tin được tới đâu",
        // cái trước nói "nói lên điều gì". Riêng số CONTRADICTS là câu trả lời trực tiếp cho câu
        // hỏi "có bằng chứng nào mâu thuẫn không?" của đề cương mục 3.5 — không log ra thì mỗi
        // lần muốn biết phải mở DB.
        Map<String, Integer> countByRelation = candidates.stream()
                .collect(Collectors.groupingBy(
                        candidate -> candidate.relation().name(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        log.info(
                "Run {}: {} lien ket ({} canh bao khong co lien ket nao), xoa {} lien ket cu, quan he {}, phuong phap {}",
                runId,
                candidates.size(),
                unlinked,
                deleted,
                countByRelation,
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

    /**
     * Tầng 2a — {@code COVERS}: dòng bị cảnh báo có được test chạy qua hay không.
     *
     * <p><b>Sinh liên kết KỂ CẢ khi dòng không được chạy</b> ({@code ci = 0}). "Không có test nào
     * đi qua dòng này" là bằng chứng quan trọng bậc nhất cho verdict {@code INSUFFICIENT_EVIDENCE}
     * — bỏ nó đi thì agent không phân biệt được "chưa ai kiểm" với "chưa nối được bằng chứng".
     *
     * <p>Trạng thái phủ (COVERED/PARTIAL/MISSED kèm mi/ci/mb/cb) đi vào {@code rationale}, KHÔNG
     * đi vào {@code weight}: weight là độ tin cậy của LIÊN KẾT, không phải mức độ phủ. Trộn hai
     * thứ đó thì mọi xếp hạng bằng chứng ở tuần 12-13 sai.
     *
     * <p>Hướng: {@code coverage → warning}, đọc là "bằng chứng độ phủ này phủ dòng của cảnh báo".
     */
    private List<LinkCandidate> covers(List<WarningLocationRow> locations, List<CoverageLineRow> coverageLines) {
        if (coverageLines.isEmpty()) {
            return List.of();
        }
        Map<String, List<CoverageLineRow>> byFile =
                coverageLines.stream().collect(Collectors.groupingBy(CoverageLineRow::filePath));

        List<LinkCandidate> candidates = new ArrayList<>();
        for (WarningLocationRow location : locations) {
            List<CoverageLineRow> inFile = byFile.get(location.filePath());
            if (inFile == null) {
                continue;
            }
            int line = location.startLine();

            Optional<CoverageLineRow> exact =
                    inFile.stream().filter(row -> row.lineNumber() == line).findFirst();
            if (exact.isPresent()) {
                CoverageLineRow row = exact.get();
                candidates.add(new LinkCandidate(
                        row.evidenceRecordId(),
                        location.evidenceRecordId(),
                        EvidenceLinkRelation.COVERS,
                        EvidenceLinkMethod.LINE_EXACT,
                        1.0,
                        "Dong %d: %s (ci=%d mi=%d mb=%d cb=%d)"
                                .formatted(line, row.statusLabel(), row.ci(), row.mi(), row.mb(), row.cb())));
                continue;
            }

            // Dòng cảnh báo không executable (khai báo, dấu ngoặc, comment) nên JaCoCo không có
            // hàng cho nó. Lấy dòng executable GẦN NHẤT trong bán kính hẹp — đủ để trả lời "vùng
            // này có được chạy không", nhưng phải hạ mức tin cậy vì nó không còn là chính dòng đó.
            Optional<CoverageLineRow> nearest = inFile.stream()
                    .filter(row -> Math.abs(row.lineNumber() - line) <= NEAREST_LINE_RADIUS)
                    .min(Comparator.comparingInt(row -> Math.abs(row.lineNumber() - line)));
            nearest.ifPresent(row -> candidates.add(new LinkCandidate(
                    row.evidenceRecordId(),
                    location.evidenceRecordId(),
                    EvidenceLinkRelation.COVERS,
                    EvidenceLinkMethod.LINE_NEAREST_EXECUTABLE,
                    0.6,
                    "Dong %d khong thuc thi duoc; dong gan nhat co do phu la %d: %s"
                            .formatted(line, row.lineNumber(), row.statusLabel()))));
        }
        return candidates;
    }

    /**
     * Tầng 2b — liên kết test ↔ cảnh báo theo QUY ƯỚC TÊN, và pass {@code CONTRADICTS}.
     *
     * <p>Đây là tầng phục hồi phần lớn bằng chứng test hỏng: 3/6 test hỏng của vulnshop không có
     * frame nào trỏ về mã production (chúng ném {@code AssertionFailedError} từ chính lớp test),
     * nên tầng 3 không với tới được.
     *
     * <p>Hai mức tin cậy, phân biệt rõ để tuần 15 đo được:
     *
     * <ul>
     *   <li>{@code METHOD_AGGREGATE} — ngoài trùng tên lớp, còn có {@code coverage_method} của
     *       đúng method bị cảnh báo và method đó ĐÃ THỰC SỰ CHẠY. Có dữ liệu chống lưng.
     *   <li>{@code NAMING_CONVENTION} — chỉ trùng tên lớp. Không có gì trong artifact chống lưng.
     * </ul>
     *
     * <p><b>{@code CONTRADICTS} đòi cả ba điều kiện</b>, và đây là chỗ dễ làm sai nhất:
     *
     * <ol>
     *   <li>Dòng cảnh báo THỰC SỰ được chạy ({@code ci > 0}). Test xanh mà không đi qua dòng đó
     *       thì không phản bác được gì.
     *   <li>Cảnh báo chưa có {@code SUPPORTS} nào. Có bằng chứng ủng hộ rồi thì một test xanh
     *       không lật ngược được.
     *   <li>Có ít nhất một test PASSED khớp theo quy ước tên.
     * </ol>
     *
     * <p><b>CONTRADICTS KHÔNG có nghĩa là false positive.</b> vulnshop cố tình đặt bẫy đúng chỗ
     * này: {@code findByUsername} có SQL injection thật, test đường đi bình thường vẫn xanh, và có
     * một test xanh khác còn CHỨNG MINH lỗ hổng có thật. Vì vậy weight của CONTRADICTS luôn thấp
     * hơn SUPPORTS, và {@code rationale} nói thẳng điều đó ra cho LLM đọc.
     */
    private List<LinkCandidate> byTestNaming(
            List<WarningLocationRow> locations,
            List<EvidenceRecord> contexts,
            List<TestCaseRow> testCases,
            List<CoverageMethodRow> coverageMethods,
            List<CoverageLineRow> coverageLines,
            List<LinkCandidate> soFar) {

        // Một lớp test cho NHIỀU ứng viên lớp production (xem TestClassNameConvention): đăng ký
        // dưới mọi ứng viên rồi để việc tra cứu quyết định cái nào thật sự tồn tại. Đoán một giá
        // trị rồi đoán sai là mất bằng chứng mà không có gì báo — đã gặp thật với
        // UserRepositoryIntegrationTest, lớp phủ đúng dòng SQL injection của vulnshop.
        Map<String, List<TestCaseRow>> testsByProductionClass = new LinkedHashMap<>();
        int unmatched = 0;
        for (TestCaseRow testCase : testCases) {
            List<String> candidates = TestClassNameConvention.productionClassCandidates(testCase.className());
            if (candidates.isEmpty()) {
                unmatched++;
                continue;
            }
            for (String candidate : candidates) {
                testsByProductionClass
                        .computeIfAbsent(TestClassNameConvention.canonical(candidate), key -> new ArrayList<>())
                        .add(testCase);
            }
        }
        if (unmatched > 0) {
            // Không nuốt: quy ước đặt tên của repo khác giả định ở đây thì bằng chứng mất trong
            // im lặng, và triệu chứng bên ngoài y hệt "dự án này không có test liên quan".
            log.info("{} lop test khong suy duoc ra lop production nao theo quy uoc ten", unmatched);
        }
        if (testsByProductionClass.isEmpty()) {
            return List.of();
        }

        Set<UUID> warningsWithSupports = soFar.stream()
                .filter(candidate -> candidate.relation() == EvidenceLinkRelation.SUPPORTS)
                .map(LinkCandidate::toEvidenceId)
                .collect(Collectors.toSet());

        Set<String> executedMethodKeys = coverageMethods.stream()
                .filter(CoverageMethodRow::executed)
                .map(row -> TestClassNameConvention.canonical(row.className()) + "#" + row.methodName())
                .collect(Collectors.toSet());

        Set<String> executedLineKeys = coverageLines.stream()
                .filter(CoverageLineRow::executed)
                .map(row -> row.filePath() + ":" + row.lineNumber())
                .collect(Collectors.toSet());

        List<LinkCandidate> candidates = new ArrayList<>();
        for (WarningLocationRow location : locations) {
            String productionClass = classOf(location, contexts);
            if (productionClass == null) {
                continue;
            }
            List<TestCaseRow> matching = testsByProductionClass.get(TestClassNameConvention.canonical(productionClass));
            if (matching == null) {
                continue;
            }
            boolean methodExecuted = location.methodName() != null
                    && executedMethodKeys.contains(
                            TestClassNameConvention.canonical(productionClass) + "#" + location.methodName());
            boolean lineExecuted = executedLineKeys.contains(location.filePath() + ":" + location.startLine());

            for (TestCaseRow testCase : matching) {
                boolean failed = testCase.status() == TestCaseStatus.FAILED;
                if (!failed) {
                    // Chỉ test XANH mới có thể phản bác, và chỉ khi ba điều kiện ở Javadoc đủ.
                    if (!lineExecuted || warningsWithSupports.contains(location.evidenceRecordId())) {
                        continue;
                    }
                }
                EvidenceLinkMethod method =
                        methodExecuted ? EvidenceLinkMethod.METHOD_AGGREGATE : EvidenceLinkMethod.NAMING_CONVENTION;
                double weight = failed
                        ? (methodExecuted ? 0.6 : 0.3)
                        : (methodExecuted ? 0.5 : 0.25);
                String rationale = failed
                        ? "Test hong %s#%s thuoc lop test cua %s%s"
                                .formatted(
                                        testCase.className(),
                                        testCase.methodName(),
                                        productionClass,
                                        methodExecuted ? " va da chay qua method bi canh bao" : " (chi khop theo ten lop)")
                        : "Test xanh %s#%s phu dong nay. KHONG chung minh canh bao sai — vulnshop co test "
                                        .formatted(testCase.className(), testCase.methodName())
                                + "xanh CHUNG MINH lo hong co that.";

                candidates.add(new LinkCandidate(
                        testCase.evidenceRecordId(),
                        location.evidenceRecordId(),
                        failed ? EvidenceLinkRelation.SUPPORTS : EvidenceLinkRelation.CONTRADICTS,
                        method,
                        weight,
                        rationale));
            }
        }
        return candidates;
    }

    /**
     * Lớp production của một vị trí cảnh báo. SpotBugs khai {@code classname} thẳng; Semgrep không
     * có, nên phải lấy từ {@code symbol} của CODE_CONTEXT bao quanh — thứ JavaParser suy ra.
     */
    private static String classOf(WarningLocationRow location, List<EvidenceRecord> contexts) {
        if (location.className() != null && !location.className().isBlank()) {
            return location.className();
        }
        // Semgrep không khai tên lớp (SARIF không có logicalLocations trong artifact thật), nên
        // lấy từ symbol của CODE_CONTEXT bao quanh — thứ JavaParser suy được thuần cú pháp. Không
        // có bước này thì mọi cảnh báo Semgrep tự động rơi khỏi tầng 2.
        return contexts.stream()
                .filter(context -> context.getSymbol() != null)
                .filter(context -> location.filePath().equals(context.getFilePath()))
                .filter(context -> context.getStartLine() != null && context.getEndLine() != null)
                .filter(context -> context.getStartLine() <= location.startLine()
                        && location.startLine() <= context.getEndLine())
                .min(Comparator.comparingInt(context -> context.getEndLine() - context.getStartLine()))
                .map(context -> {
                    String symbol = context.getSymbol();
                    int hash = symbol.indexOf('#');
                    return hash < 0 ? symbol : symbol.substring(0, hash);
                })
                .orElse(null);
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
