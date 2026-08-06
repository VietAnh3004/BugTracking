package vn.hust.datn.evidence.service.linker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.StackFrameKind;
import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.domain.TestFailureKind;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.service.analysis.CodeContextDraft;
import vn.hust.datn.evidence.service.analysis.EvidenceDraft;
import vn.hust.datn.evidence.service.analysis.EvidenceMaterializer;
import vn.hust.datn.evidence.service.analysis.SnapshotPathResolver;
import vn.hust.datn.evidence.service.analysis.StackFrameDraft;
import vn.hust.datn.evidence.service.analysis.StackTraceDraft;
import vn.hust.datn.evidence.service.analysis.TestCaseDraft;
import vn.hust.datn.evidence.service.analysis.WarningDraft;
import vn.hust.datn.evidence.service.analysis.WarningLocationDraft;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Ba tầng của EvidenceLinker trên dữ liệu DỰNG TAY.
 *
 * <h2>Vì sao không dùng vulnshop cho tầng 3</h2>
 *
 * Trên artifact thật, tầng 3 sinh ĐÚNG 0 liên kết — và đó là dữ liệu đúng, không phải lỗi. Hai
 * frame production duy nhất của vulnshop trỏ tới {@code CartService.java:25} và
 * {@code UserService.java:30}, mà không analyzer nào cảnh báo trên hai file đó: chúng là TP-11 và
 * TP-13 của {@code SEEDED.md}, đúng nhóm lỗi "KHÔNG công cụ nào bắt, chỉ test hỏng phát hiện".
 *
 * <p>Nên nếu chỉ test bằng vulnshop thì tầng 3 xanh mà chưa từng chạy một nhánh nào — đúng loại
 * test xanh giả. Ở đây dựng tay đủ ba mảnh (cảnh báo, test hỏng, stack frame trỏ đúng dòng) để
 * thuật toán thực sự bị thực thi.
 *
 * <p>Dựng qua {@code EvidenceMaterializer} với draft thật chứ không INSERT tay: nhờ vậy test đi
 * qua đúng đường ghi của production, gồm cả việc quy {@code traceCorrelationId} thành
 * {@code test_case.stack_trace_evidence_id}.
 */
class EvidenceLinkerTest extends AbstractIntegrationTest {

    private static final String FILE = "src/main/java/app/Foo.java";
    private static final String TEST_FILE = "src/test/java/app/FooTest.java";

    @Autowired
    private EvidenceLinker linker;

    @Autowired
    private EvidenceMaterializer materializer;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AnalysisRun run;
    private SnapshotPathResolver pathResolver;

    @BeforeEach
    void setUp() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.readySnapshotWithFiles(project, List.of(FILE, TEST_FILE));
        run = fixtures.runWithStatus(snapshot, vn.hust.datn.evidence.domain.AnalysisRunStatus.RUNNING);
        pathResolver = SnapshotPathResolver.of(List.of(FILE, TEST_FILE));
    }

    private WarningDraft warning(int line) {
        return new WarningDraft(
                EvidenceType.STATIC_WARNING,
                EvidenceSource.spotbugs,
                FILE,
                line,
                line,
                "app.Foo#doThing",
                "Canh bao tai dong " + line,
                Map.of(),
                WarningTool.spotbugs,
                "SOME_RULE",
                "SOME_RULE",
                "1",
                WarningSeverity.HIGH,
                List.of(),
                true,
                0,
                List.of(new WarningLocationDraft(0, null, true, FILE, line, line, "app.Foo", "doThing")));
    }

    private CodeContextDraft codeContext(int fromLine, int toLine) {
        return new CodeContextDraft(
                EvidenceType.CODE_CONTEXT,
                EvidenceSource.parser,
                FILE,
                fromLine,
                toLine,
                "app.Foo#doThing",
                "Ngu canh app.Foo#doThing",
                Map.of("kind", "METHOD"),
                "METHOD",
                "doThing");
    }

    private List<EvidenceDraft> failedTestWithFrameAt(int line, String correlationId) {
        String rawLine = "at app.Foo.doThing(Foo.java:%d)".formatted(line);
        TestCaseDraft testCase = new TestCaseDraft(
                EvidenceType.FAILED_TEST,
                EvidenceSource.surefire,
                TEST_FILE,
                1,
                1,
                "app.FooTest#doThing_hong",
                "Test hong app.FooTest#doThing_hong",
                Map.of(),
                "app.FooTest",
                "doThing_hong",
                "doThing_hong",
                TestCaseStatus.FAILED,
                TestFailureKind.ERROR,
                "java.lang.ArithmeticException",
                "/ by zero",
                rawLine,
                2,
                "TEST-app.FooTest.xml",
                correlationId);

        StackTraceDraft trace = new StackTraceDraft(
                EvidenceType.STACK_TRACE,
                EvidenceSource.surefire,
                FILE,
                line,
                line,
                "app.Foo#doThing",
                "Stack trace cua app.FooTest#doThing_hong",
                Map.of(),
                correlationId,
                "java.lang.ArithmeticException",
                "/ by zero",
                List.of(new StackFrameDraft(0, rawLine, null, "app.Foo", "doThing", "Foo.java", line, StackFrameKind.PRODUCTION)));

        return List.of(testCase, trace);
    }

    private List<Map<String, Object>> linksOf(UUID runId) {
        return jdbcTemplate.queryForList(
                """
                SELECT l.relation, l.method, l.weight, l.rationale,
                       f.type AS from_type, t.type AS to_type
                  FROM evidence_link l
                  JOIN evidence_record f ON f.id = l.from_evidence_id
                  JOIN evidence_record t ON t.id = l.to_evidence_id
                 WHERE l.analysis_run_id = ?
                 ORDER BY l.method
                """,
                runId);
    }

    @Test
    void tang1_canhBaoNamTrongMethod_sinhLocatedIn() {
        materializer.materialize(run.getId(), List.of(warning(10), codeContext(5, 20)), pathResolver);

        LinkResult result = linker.link(run.getId());

        assertThat(result.linkCount()).isEqualTo(1);
        assertThat(result.unlinkedWarningCount()).isZero();
        Map<String, Object> link = linksOf(run.getId()).getFirst();
        assertThat(link.get("relation")).isEqualTo(EvidenceLinkRelation.LOCATED_IN.name());
        assertThat(link.get("method")).isEqualTo(EvidenceLinkMethod.LINE_EXACT.name());
        assertThat(link.get("from_type")).isEqualTo(EvidenceType.STATIC_WARNING.name());
        assertThat(link.get("to_type")).isEqualTo(EvidenceType.CODE_CONTEXT.name());
    }

    @Test
    void tang1_canhBaoNgoaiMoiNguCanh_khongSinhLienKet() {
        materializer.materialize(run.getId(), List.of(warning(99), codeContext(5, 20)), pathResolver);

        LinkResult result = linker.link(run.getId());

        assertThat(result.linkCount()).isZero();
        assertThat(result.unlinkedWarningCount()).isEqualTo(1);
    }

    /**
     * Nhánh chính của tầng 3. Hướng phải là {@code FAILED_TEST → STATIC_WARNING}, KHÔNG phải
     * {@code STACK_TRACE → STATIC_WARNING}: giao diện "test liên quan" phải hiện ra tên test chứ
     * không phải một stack trace.
     */
    @Test
    void tang3_frameProductionTrungDongCanhBao_sinhSupportsTuTest() {
        List<EvidenceDraft> drafts = new java.util.ArrayList<>(List.of(warning(10), codeContext(5, 20)));
        drafts.addAll(failedTestWithFrameAt(10, "trace-1"));

        materializer.materialize(run.getId(), drafts, pathResolver);
        LinkResult result = linker.link(run.getId());

        List<Map<String, Object>> supports = linksOf(run.getId()).stream()
                .filter(link -> EvidenceLinkRelation.SUPPORTS.name().equals(link.get("relation")))
                .toList();

        assertThat(supports).hasSize(1);
        assertThat(supports.getFirst().get("method")).isEqualTo(EvidenceLinkMethod.STACK_FRAME.name());
        assertThat(supports.getFirst().get("from_type")).isEqualTo(EvidenceType.FAILED_TEST.name());
        assertThat(supports.getFirst().get("to_type")).isEqualTo(EvidenceType.STATIC_WARNING.name());
        assertThat((String) supports.getFirst().get("rationale")).contains("Foo.java:10");
        assertThat(result.countByMethod()).containsEntry(EvidenceLinkMethod.STACK_FRAME.name(), 1);
    }

    @Test
    void tang3_frameOFileKhacKhongLienQuan_khongSinhLienKet() {
        List<EvidenceDraft> drafts = new java.util.ArrayList<>(List.of(warning(10), codeContext(5, 20)));
        drafts.addAll(failedTestWithFrameAt(77, "trace-1"));

        materializer.materialize(run.getId(), drafts, pathResolver);
        linker.link(run.getId());

        assertThat(linksOf(run.getId()))
                .noneMatch(link -> EvidenceLinkRelation.SUPPORTS.name().equals(link.get("relation")));
    }

    /**
     * Cảnh báo chỉ có bằng chứng từ TEST (nằm ở đầu {@code to} của liên kết) vẫn phải được tính là
     * "đã có liên kết". Đếm nhầm ở đây làm bộ đếm sức khoẻ báo động giả.
     */
    @Test
    void canhBaoChiCoBangChungTuTest_khongBiTinhLaTroTroi() {
        List<EvidenceDraft> drafts = new java.util.ArrayList<>(List.of(warning(10)));
        drafts.addAll(failedTestWithFrameAt(10, "trace-1"));

        materializer.materialize(run.getId(), drafts, pathResolver);
        LinkResult result = linker.link(run.getId());

        assertThat(result.linkCount()).isEqualTo(1);
        assertThat(result.unlinkedWarningCount()).isZero();
    }

    /** Chạy lại phải cho kết quả GIỐNG HỆT, và không được vỡ UNIQUE (from, to, relation). */
    @Test
    void chayLaiHaiLan_ketQuaGiongHetVaKhongVoRangBuoc() {
        List<EvidenceDraft> drafts = new java.util.ArrayList<>(List.of(warning(10), codeContext(5, 20)));
        drafts.addAll(failedTestWithFrameAt(10, "trace-1"));
        materializer.materialize(run.getId(), drafts, pathResolver);

        LinkResult first = linker.link(run.getId());
        LinkResult second = linker.link(run.getId());

        assertThat(second.linkCount()).isEqualTo(first.linkCount());
        assertThat(second.countByMethod()).isEqualTo(first.countByMethod());
        assertThat(second.deletedCount()).isEqualTo(first.linkCount());
        assertThat(linksOf(run.getId())).hasSize(first.linkCount());
    }

    /**
     * Một cảnh báo có NHIỀU vị trí trong cùng một method chỉ được sinh MỘT liên kết — đây là ca
     * làm vỡ {@code UNIQUE (from, to, relation)} nếu không dedup trong bộ nhớ.
     */
    @Test
    void motCanhBaoNhieuViTriCungMethod_dedupThanhMotLienKet() {
        WarningDraft nhieuViTri = new WarningDraft(
                EvidenceType.STATIC_WARNING,
                EvidenceSource.spotbugs,
                FILE,
                10,
                10,
                "app.Foo#doThing",
                "Canh bao nhieu vi tri",
                Map.of(),
                WarningTool.spotbugs,
                "OBL_UNSATISFIED_OBLIGATION",
                "OBL_UNSATISFIED_OBLIGATION",
                "1",
                WarningSeverity.HIGH,
                List.of(),
                true,
                0,
                List.of(
                        new WarningLocationDraft(0, null, true, FILE, 10, 10, "app.Foo", "doThing"),
                        new WarningLocationDraft(1, "SOURCE_LINE_PATH_CONTINUES", false, FILE, 12, 12, "app.Foo", "doThing"),
                        new WarningLocationDraft(2, "SOURCE_LINE_PATH_CONTINUES", false, FILE, 15, 15, "app.Foo", "doThing")));

        materializer.materialize(run.getId(), List.of(nhieuViTri, codeContext(5, 20)), pathResolver);
        LinkResult result = linker.link(run.getId());

        assertThat(result.linkCount()).isEqualTo(1);
    }

    /** Mọi giá trị của enum phải có mặt trong bảng thứ bậc, nếu không dedup sẽ so sai. */
    @Test
    void bangThuBacPhuHetMoiGiaTriCuaEnum() {
        assertThat(LinkAccumulator.precedence()).containsExactlyInAnyOrder(EvidenceLinkMethod.values());
    }
}
