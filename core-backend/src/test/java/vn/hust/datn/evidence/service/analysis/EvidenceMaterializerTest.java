package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.StackFrameKind;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Chạy TOÀN BỘ bốn artifact thật của vulnshop qua materializer rồi đối chiếu từng bảng với
 * {@code expected-analysis.json}.
 *
 * <p>Đây là chốt chặn quan trọng nhất của giai đoạn B: bốn parser đã có test riêng chứng minh
 * chúng đọc đúng, nhưng chỉ test này chứng minh dữ liệu đó <b>xuống được DB nguyên vẹn</b> qua
 * bảy bảng, bảy ràng buộc và một chỉ mục đường dẫn thật.
 */
class EvidenceMaterializerTest extends AbstractIntegrationTest {

    @Autowired
    private EvidenceMaterializer materializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestFixtures fixtures;

    private RepositorySnapshot snapshot;
    private UUID runId;
    private SnapshotPathResolver pathResolver;

    @BeforeEach
    void chuanBiRunDangChay() {
        Project project = fixtures.project("materializer-" + UUID.randomUUID());
        snapshot = fixtures.snapshot(project);
        runId = fixtures.runWithStatus(snapshot, AnalysisRunStatus.RUNNING).getId();
        pathResolver = SnapshotPathResolver.of(SampleArtifacts.vulnshopSourcePaths());
    }

    /**
     * Thứ tự CỐ ĐỊNH spotbugs → semgrep → surefire → jacoco. Thứ tự này trở thành
     * {@code seq_in_run}, tức trở thành Evidence ID mà LLM trích dẫn — đổi thứ tự là đổi ID của
     * mọi bằng chứng.
     */
    private List<EvidenceDraft> parseAllArtifacts() {
        List<EvidenceDraft> drafts = new ArrayList<>();
        drafts.addAll(new SpotbugsXmlAdapter().parse(SampleArtifacts.spotbugsXml()));
        drafts.addAll(new SemgrepSarifAdapter().parse(SampleArtifacts.semgrepSarif()));
        SurefireResultCollector surefire = new SurefireResultCollector();
        SampleArtifacts.surefireReports()
                .forEach(report -> drafts.addAll(surefire.collect(SampleArtifacts.bytesOf(report))));
        drafts.addAll(new JacocoCoverageCollector().collect(SampleArtifacts.jacocoXml()));
        return drafts;
    }

    private int countInRun(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM %s x JOIN evidence_record e ON e.id = x.evidence_record_id "
                                .formatted(table)
                        + "WHERE e.analysis_run_id = ?",
                Integer.class,
                runId);
    }

    @Test
    void materialize_bonArtifactThat_ghiDuSoHangBayBang() {
        List<EvidenceDraft> drafts = parseAllArtifacts();

        MaterializationResult result = materializer.materialize(runId, drafts, pathResolver);

        int spotbugs = SampleArtifacts.expectedInt("spotbugs", "findings");
        int semgrep = SampleArtifacts.expectedInt("semgrep", "findings");
        int tests = SampleArtifacts.expectedInt("tests", "total");
        int failedTests = SampleArtifacts.expectedInt("tests", "failed");

        // 90 = 28 warning SpotBugs + 5 warning Semgrep + 33 test_case + 5 stack trace + 19 coverage.
        assertThat(result.evidenceCount()).isEqualTo(spotbugs + semgrep + tests + failedTests + 19);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_record WHERE analysis_run_id = ?", Integer.class, runId))
                .isEqualTo(result.evidenceCount());

        assertThat(countInRun("warning")).isEqualTo(spotbugs + semgrep);
        assertThat(countInRun("warning_location")).isEqualTo(38);
        assertThat(countInRun("test_case")).isEqualTo(tests);
        assertThat(countInRun("stack_frame")).isEqualTo(44);
        assertThat(countInRun("coverage_line")).isEqualTo(SampleArtifacts.expectedInt("coverage", "total_lines"));
        assertThat(countInRun("coverage_method")).isEqualTo(73);
    }

    /**
     * {@code finding_count} chỉ đếm {@code STATIC_WARNING}. Đếm cả 90 thì
     * {@code ck_run_not_silently_empty} vẫn xanh ngay cả khi CẢ HAI analyzer tĩnh trả rỗng mà test
     * vẫn chạy — tức ràng buộc chống-run-rỗng mất tác dụng đúng lúc cần nhất.
     */
    @Test
    void materialize_findingCountChiDemStaticWarningChuKhongPhaiMoiEvidence() {
        MaterializationResult result = materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        int staticWarnings =
                SampleArtifacts.expectedInt("spotbugs", "findings") + SampleArtifacts.expectedInt("semgrep", "findings");
        assertThat(result.findingCount()).isEqualTo(staticWarnings);
        assertThat(result.findingCount()).isLessThan(result.evidenceCount());
        assertThat(result.countByType())
                .containsEntry(EvidenceType.STATIC_WARNING, staticWarnings)
                .containsEntry(EvidenceType.COVERAGE_INFORMATION, 19)
                .containsEntry(EvidenceType.FAILED_TEST, SampleArtifacts.expectedInt("tests", "failed"));
    }

    /** {@code seq_in_run} là nguồn sinh {@code short_id} — thủng một số là thủng một Evidence ID. */
    @Test
    void materialize_seqInRunLienTucTu1_vaShortIdDuyNhat() {
        MaterializationResult result = materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        List<Integer> sequences = jdbcTemplate.queryForList(
                "SELECT seq_in_run FROM evidence_record WHERE analysis_run_id = ? ORDER BY seq_in_run",
                Integer.class,
                runId);
        assertThat(sequences).hasSize(result.evidenceCount());
        assertThat(sequences.get(0)).isEqualTo(1);
        assertThat(sequences.get(sequences.size() - 1)).isEqualTo(result.evidenceCount());

        List<String> shortIds = jdbcTemplate.queryForList(
                "SELECT short_id FROM evidence_record WHERE analysis_run_id = ?", String.class, runId);
        assertThat(shortIds).doesNotHaveDuplicates().allMatch(id -> id.startsWith("EV-"));
    }

    /**
     * Bảo chứng TÁI LẬP cho phần thực nghiệm tuần 15-16: cùng một snapshot phải cho cùng một bộ
     * Evidence ID. Bắt được cả nguồn bất định trong parser (duyệt {@code HashMap}) lẫn trong
     * materializer.
     */
    @Test
    void materialize_chayHaiLan_choKetQuaGiongHetNhau() {
        UUID secondRunId =
                fixtures.runWithStatus(snapshot, AnalysisRunStatus.RUNNING).getId();

        materializer.materialize(runId, parseAllArtifacts(), pathResolver);
        materializer.materialize(secondRunId, parseAllArtifacts(), pathResolver);

        // CỐ Ý không so short_id: ký tự cuối của nó băm từ UUID ngẫu nhiên của từng hàng, nên nó
        // KHÔNG tái lập được giữa hai run — đó là thiết kế chống LLM bịa ID (V2__evidence_model.sql),
        // không phải khiếm khuyết. Thứ phải tái lập là seq_in_run và nội dung bằng chứng.
        String projection =
                """
                SELECT seq_in_run AS seq, type, source, coalesce(file_path, '') AS file_path,
                       coalesce(start_line, -1) AS start_line, coalesce(symbol, '') AS symbol,
                       summary, content::text AS content
                FROM evidence_record WHERE analysis_run_id = ? ORDER BY seq_in_run
                """;
        assertThat(jdbcTemplate.queryForList(projection, runId))
                .isEqualTo(jdbcTemplate.queryForList(projection, secondRunId));
    }

    /**
     * Chứng minh {@code ck_run_paths_not_all_unresolved} là ràng buộc DB THẬT. Chuẩn hoá đường dẫn
     * hỏng 100% cho ra một Evidence Store trông đầy đủ nhưng không bằng chứng nào bấm được và
     * EvidenceLinker sinh 0 liên kết — ADR 0004 gọi đích danh đây là rủi ro lớn nhất còn lại.
     */
    @Test
    void materialize_toanBoPathHong_runKhongTheChuyenSangSUCCEEDED() {
        SnapshotPathResolver snapshotRong = SnapshotPathResolver.of(List.of());

        MaterializationResult result = materializer.materialize(runId, parseAllArtifacts(), snapshotRong);

        assertThat(result.locationCount()).isPositive();
        assertThat(result.unresolvedLocationCount()).isEqualTo(result.locationCount());
        assertThat(result.allLocationsUnresolved()).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        UPDATE analysis_run
                        SET status = 'SUCCEEDED', finding_count = ?, location_count = ?,
                            unresolved_location_count = ?
                        WHERE id = ?
                        """,
                        result.findingCount(),
                        result.locationCount(),
                        result.unresolvedLocationCount(),
                        runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_run_paths_not_all_unresolved");
    }

    /** Cây nguồn thật của vulnshop phải đối chiếu được 100% — 0 vị trí hỏng là tiêu chí nghiệm thu. */
    @Test
    void materialize_cayNguonThat_moiViTriDeuDoiChieuDuoc() {
        MaterializationResult result = materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        assertThat(result.unresolvedLocationCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM warning_location l JOIN evidence_record e ON e.id = l.evidence_record_id "
                                + "WHERE e.analysis_run_id = ? AND l.file_path IS NULL",
                        Integer.class,
                        runId))
                .isZero();
    }

    /**
     * Frame JDK/JUnit/thư viện KHÔNG được tính vào mẫu số. Nếu tính, một run hoàn toàn đúng vẫn
     * báo ~84% vị trí hỏng và bộ đếm mất sạch giá trị cảnh báo.
     */
    @Test
    void materialize_frameJdkVaJunitKhongTinhVaoLocationCount() {
        List<EvidenceDraft> drafts = parseAllArtifacts();
        long frameCoTheDoiChieu = drafts.stream()
                .filter(StackTraceDraft.class::isInstance)
                .map(StackTraceDraft.class::cast)
                .flatMap(trace -> trace.frames().stream())
                .filter(frame -> frame.rawFilePath() != null)
                .filter(frame -> frame.frameKind() == StackFrameKind.PRODUCTION || frame.frameKind() == StackFrameKind.TEST)
                .count();

        MaterializationResult result = materializer.materialize(runId, drafts, pathResolver);

        // Nếu con số này bằng 0 thì hai assert dưới đây xanh một cách vô nghĩa: 2/5 test hỏng
        // thật CÓ frame trỏ về mã production (CartService.java:25, UserService.java:30).
        assertThat(frameCoTheDoiChieu).isPositive();
        // 38 warning_location + 33 test_case + 19 coverage + frame thuộc dự án.
        assertThat(result.locationCount()).isEqualTo(38 + 33 + 19 + (int) frameCoTheDoiChieu);
        assertThat(result.locationCount()).isLessThan(38 + 33 + 19 + 44);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM stack_frame f JOIN evidence_record e ON e.id = f.evidence_record_id "
                                + "WHERE e.analysis_run_id = ? AND f.frame_kind = 'JDK' AND f.file_path IS NOT NULL",
                        Integer.class,
                        runId))
                .isZero();
    }

    /**
     * {@code test_case.stack_trace_evidence_id} là provenance CẤU TRÚC — nhờ nó tuần 8-9 nối được
     * test hỏng với stack trace mà không cần EvidenceLinker (tuần 10-11).
     */
    @Test
    void materialize_testHongDuocNoiVoiEvidenceStackTraceTuongUng() {
        materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        List<Map<String, Object>> noiDuoc = jdbcTemplate.queryForList(
                """
                SELECT tc.class_name, tc.method_name, trace.type AS trace_type
                FROM test_case tc
                JOIN evidence_record e     ON e.id = tc.evidence_record_id
                JOIN evidence_record trace ON trace.id = tc.stack_trace_evidence_id
                WHERE e.analysis_run_id = ? AND tc.status = 'FAILED'
                """,
                runId);

        assertThat(noiDuoc).hasSize(SampleArtifacts.expectedInt("tests", "failed"));
        assertThat(noiDuoc).allSatisfy(row -> assertThat(row).containsEntry("trace_type", "STACK_TRACE"));

        // ck_test_case_trace_only_when_failed đã cấm ở tầng DB; khẳng định lại để lỗi ghi nhầm
        // hiện ra ở đây thay vì thành một ràng buộc bị vi phạm ở tận nơi khác.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM test_case tc JOIN evidence_record e ON e.id = tc.evidence_record_id "
                                + "WHERE e.analysis_run_id = ? AND tc.status <> 'FAILED' AND tc.stack_trace_evidence_id IS NOT NULL",
                        Integer.class,
                        runId))
                .isZero();
    }

    /**
     * {@code coverage_line.status} do Postgres sinh, không ghi từ tầng ứng dụng — đọc lại từ DB
     * để chứng minh công thức trong migration khớp với công thức parser dùng để tính summary.
     */
    @Test
    void materialize_statusCuaCoverageLineDoPostgresSinhVaKhopVoiJaCoCo() {
        materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        Map<String, Object> phanBo = jdbcTemplate.queryForMap(
                """
                SELECT count(*) FILTER (WHERE l.status = 'COVERED') AS covered,
                       count(*) FILTER (WHERE l.status = 'PARTIAL') AS partial,
                       count(*) FILTER (WHERE l.status = 'MISSED')  AS missed
                FROM coverage_line l JOIN evidence_record e ON e.id = l.evidence_record_id
                WHERE e.analysis_run_id = ?
                """,
                runId);

        assertThat(phanBo.get("covered")).isEqualTo(77L);
        assertThat(phanBo.get("partial")).isEqualTo(5L);
        assertThat(phanBo.get("missed")).isEqualTo(89L);
        // "Được phủ" theo JaCoCo là ci > 0 = COVERED + PARTIAL. Ai "sửa" thành 77 thì đỏ ở đây.
        assertThat((Long) phanBo.get("covered") + (Long) phanBo.get("partial"))
                .isEqualTo((long) SampleArtifacts.expectedInt("coverage", "covered_lines"));
    }

    /** {@code cwe_ids INT[]} phải ra mảng Postgres thật, không phải chuỗi — và NULL khi tool không khai. */
    @Test
    void materialize_cweIdsGhiThanhMangPostgresVaNullKhiThieu() {
        List<EvidenceDraft> drafts = parseAllArtifacts();
        long thieuCweTrongDraft = drafts.stream()
                .filter(WarningDraft.class::isInstance)
                .map(WarningDraft.class::cast)
                .filter(warning -> warning.cweIds() == null || warning.cweIds().isEmpty())
                .count();

        materializer.materialize(runId, drafts, pathResolver);

        // 8/28 BugInstance thật thiếu @cweid. NULL và mảng RỖNG phải phân biệt được: "tool không
        // khai CWE" khác hẳn "tool khai một danh sách rỗng".
        assertThat(thieuCweTrongDraft).isEqualTo(8);
        assertThat(countWarnings("w.cwe_ids IS NULL")).isEqualTo((int) thieuCweTrongDraft);
        assertThat(countWarnings("array_length(w.cwe_ids, 1) >= 1")).isEqualTo(33 - (int) thieuCweTrongDraft);
        assertThat(countWarnings("w.cwe_ids = '{}'")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM warning w JOIN evidence_record e ON e.id = w.evidence_record_id "
                                + "WHERE e.analysis_run_id = ? AND 328 = ANY (w.cwe_ids)",
                        Integer.class,
                        runId))
                .isPositive();
    }

    private int countWarnings(String condition) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM warning w JOIN evidence_record e ON e.id = w.evidence_record_id "
                        + "WHERE e.analysis_run_id = ? AND " + condition,
                Integer.class,
                runId);
    }

    /**
     * {@code path_match_kind} cố ý không lên cột DB (xem {@link PathMatchKind}) nhưng vẫn phải
     * truy vấn được từ JSONB — nếu không, tuần 15 không đo được tỉ lệ khớp tin cậy thấp.
     *
     * <p>Dùng {@code jsonb_exists(...)} chứ KHÔNG dùng toán tử {@code ?} của Postgres: trong JDBC
     * dấu {@code ?} luôn bị hiểu là placeholder tham số, nên câu lệnh sẽ hỏng lúc chạy.
     */
    @Test
    void materialize_contentMangPathMatchKindDeDoDuocDoTinCay() {
        materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        List<String> kinds = jdbcTemplate.queryForList(
                "SELECT DISTINCT content->>'path_match_kind' FROM evidence_record "
                        + "WHERE analysis_run_id = ? AND jsonb_exists(content, 'path_match_kind')",
                String.class,
                runId);

        assertThat(kinds).isNotEmpty().doesNotContainNull();
        assertThat(kinds).allSatisfy(kind -> assertThat(PathMatchKind.valueOf(kind)).isNotNull());
        // 3/5 test hỏng không có frame production nào (phát hiện 2) -> evidence STACK_TRACE của
        // chúng KHÔNG có vị trí, nên cũng không có khoá này. Đó là dữ liệu đúng, không phải thiếu.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_record "
                                + "WHERE analysis_run_id = ? AND NOT jsonb_exists(content, 'path_match_kind')",
                        Integer.class,
                        runId))
                .isEqualTo(3);
    }

    /** Không có draft nào thì không có hàng nào — và không nổ. */
    @Test
    void materialize_danhSachRong_khongGhiGiVaKhongNem() {
        MaterializationResult result = materializer.materialize(runId, List.of(), pathResolver);

        assertThat(result.evidenceCount()).isZero();
        assertThat(result.findingCount()).isZero();
        assertThat(result.allLocationsUnresolved()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_record WHERE analysis_run_id = ?", Integer.class, runId))
                .isZero();
    }

    /** Run vẫn còn RUNNING sau materialize: quyết định trạng thái là việc của tầng điều phối. */
    @Test
    void materialize_khongTuDoiTrangThaiRun() {
        materializer.materialize(runId, parseAllArtifacts(), pathResolver);

        AnalysisRun run = fixtures.reloadRun(runId);
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.RUNNING);
        assertThat(run.getFindingCount()).isZero();
        assertThat(run.getLocationCount()).isZero();
    }
}
