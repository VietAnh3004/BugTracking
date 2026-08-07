package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.WarningView;
import vn.hust.datn.evidence.exception.EvidenceNotAWarningException;
import vn.hust.datn.evidence.exception.EvidenceNotFoundException;
import vn.hust.datn.evidence.exception.EvidenceWrongRunException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.exception.RunNotQueryableException;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Bốn nhánh lỗi của 5 tool là dữ liệu, không chỉ HTTP status — mỗi nhánh tương ứng một
 * {@code ClaimCitationResolution} mà CitationValidator (tuần 12-13) dùng lại nguyên xi. Test này
 * xác nhận từng nhánh ném đúng exception TRƯỚC KHI có controller nào bọc quanh nó.
 */
class EvidenceQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EvidenceQueryService service;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void getWarning_thanhCongTraVeDungGiaTri() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "WEAK_MESSAGE_DIGEST_MD5", WarningTool.spotbugs, "PasswordHasher.java", 40);

        WarningView result = service.getWarning(run.getId(), warning.getEvidenceRecord().getShortId());

        assertThat(result.ruleId()).isEqualTo("WEAK_MESSAGE_DIGEST_MD5");
        assertThat(result.filePath()).isEqualTo("PasswordHasher.java");
    }

    @Test
    void getWarning_shortIdKhongTonTai_nemEvidenceNotFound() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);

        assertThatThrownBy(() -> service.getWarning(run.getId(), "EV-KHONG-TON-TAI"))
                .isInstanceOf(EvidenceNotFoundException.class);
    }

    @Test
    void getWarning_shortIdOChayKhac_nemEvidenceWrongRun() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshotA = fixtures.snapshot(project);
        AnalysisRun runA = fixtures.succeededRun(snapshotA, 1);
        Warning warning = fixtures.warning(runA, "SQL_INJECTION_SPRING_JDBC", WarningTool.spotbugs, "UserRepository.java", 35);

        RepositorySnapshot snapshotB = fixtures.snapshot(project);
        AnalysisRun runB = fixtures.succeededRun(snapshotB, 1);

        assertThatThrownBy(() -> service.getWarning(runB.getId(), warning.getEvidenceRecord().getShortId()))
                .isInstanceOf(EvidenceWrongRunException.class);
    }

    @Test
    void getWarning_evidenceKhongPhaiWarning_nemEvidenceNotAWarning() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        EvidenceRecord testEvidence = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "CartServiceTest.java", 37);

        assertThatThrownBy(() -> service.getWarning(run.getId(), testEvidence.getShortId()))
                .isInstanceOf(EvidenceNotAWarningException.class);
    }

    @Test
    void getWarning_runDangChay_nemRunNotQueryable() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.runWithStatus(snapshot, AnalysisRunStatus.RUNNING);

        assertThatThrownBy(() -> service.getWarning(run.getId(), "EV-BAT-KY"))
                .isInstanceOf(RunNotQueryableException.class);
    }

    @Test
    void getWarning_runKhongTonTai_nemResourceNotFound() {
        assertThatThrownBy(() -> service.getWarning(UUID.randomUUID(), "EV-BAT-KY"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRelatedTests_traVeMethodVaWeight() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);

        Warning warning = fixtures.warning(run, "ES_COMPARING_PARAMETER_STRING_WITH_EQ", WarningTool.spotbugs, "OrderStatusChecker.java", 18);
        EvidenceRecord failedTest = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "OrderStatusCheckerTest.java", 42);
        fixtures.link(
                failedTest, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.NAMING_CONVENTION, 0.4);

        var items = service.getRelatedTests(run.getId(), warning.getEvidenceRecord().getShortId(), Set.of(EvidenceLinkRelation.SUPPORTS));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().method()).isEqualTo(EvidenceLinkMethod.NAMING_CONVENTION);
        assertThat(items.getFirst().weight()).isEqualTo(0.4);
        assertThat(items.getFirst().shortId()).isEqualTo(failedTest.getShortId());
    }

    /**
     * Hồi quy: một CẶP evidence có thể có NHIỀU link khác {@code relation} —
     * {@code UNIQUE (from, to, relation)} cho phép đúng điều đó, và EvidenceLinker tuần 10-11 sinh
     * ra tình huống này thật (một test vừa được stack frame ủng hộ, vừa phủ dòng cảnh báo).
     *
     * <p>Bản trước gom link vào {@code Map<UUID, EvidenceLink>} khoá theo evidence phía đối diện,
     * nên link sau ĐÈ link trước và một quan hệ biến mất IM LẶNG. Đơn vị trả về phải là MỘT LINK,
     * không phải một evidence.
     */
    @Test
    void getRelatedTests_motCapCoHaiQuanHe_traVeCaHai() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);

        Warning warning = fixtures.warning(run, "SQL_INJECTION_SPRING_JDBC", WarningTool.spotbugs, "UserRepository.java", 35);
        EvidenceRecord test = fixtures.evidenceRecord(run, EvidenceType.TEST_RESULT, EvidenceSource.surefire, "UserRepositoryTest.java", 20);

        fixtures.link(test, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.STACK_FRAME, 1.0);
        fixtures.link(test, warning.getEvidenceRecord(), EvidenceLinkRelation.CONTRADICTS, EvidenceLinkMethod.NAMING_CONVENTION, 0.25);

        var items = service.getRelatedTests(run.getId(), warning.getEvidenceRecord().getShortId(), null);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(item -> item.relation())
                .containsExactlyInAnyOrder(EvidenceLinkRelation.SUPPORTS, EvidenceLinkRelation.CONTRADICTS);
        assertThat(items).extracting(item -> item.method())
                .containsExactlyInAnyOrder(EvidenceLinkMethod.STACK_FRAME, EvidenceLinkMethod.NAMING_CONVENTION);
    }

    /**
     * Thứ tự phải TẤT ĐỊNH: đây là dữ liệu đi thẳng vào prompt LLM. Thứ tự đổi giữa hai lần chạy
     * thì thí nghiệm tái lập ở tuần 15-16 mất căn cứ — mà lỗi đó không có triệu chứng nào.
     *
     * <p>Sắp theo {@code relation}, rồi {@code weight} GIẢM DẦN (bằng chứng mạnh lên trước),
     * rồi {@code short_id} để phá hoà hoàn toàn.
     */
    @Test
    void getRelatedTests_sapTatDinhTheoWeightGiamDan() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);

        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 27);
        EvidenceRecord yeu = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "ATest.java", 1);
        EvidenceRecord manh = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "BTest.java", 2);

        fixtures.link(yeu, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.NAMING_CONVENTION, 0.3);
        fixtures.link(manh, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.STACK_FRAME, 1.0);

        var items = service.getRelatedTests(run.getId(), warning.getEvidenceRecord().getShortId(), null);

        assertThat(items).extracting(item -> item.shortId()).containsExactly(manh.getShortId(), yeu.getShortId());
    }

    /**
     * "Test liên quan" phải là TEST, không phải bất kỳ evidence nào tình cờ mang quan hệ SUPPORTS.
     * Bản trước truyền {@code typeFilter = null} nên một bằng chứng độ phủ mang SUPPORTS cũng lọt
     * vào danh sách test — LLM đọc xong sẽ nói "có test ủng hộ" trong khi không có test nào.
     */
    @Test
    void getRelatedTests_loaiEvidenceKhongPhaiTest() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);

        Warning warning = fixtures.warning(run, "DM_CONVERT_CASE", WarningTool.spotbugs, "TextNormalizer.java", 10);
        EvidenceRecord test = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "TextNormalizerTest.java", 12);
        EvidenceRecord coverage = fixtures.evidenceRecord(run, EvidenceType.COVERAGE_INFORMATION, EvidenceSource.jacoco, "TextNormalizer.java", 10);

        fixtures.link(test, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.STACK_FRAME, 1.0);
        fixtures.link(coverage, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.METHOD_AGGREGATE, 0.6);

        var items = service.getRelatedTests(run.getId(), warning.getEvidenceRecord().getShortId(), null);

        assertThat(items).extracting(item -> item.shortId()).containsExactly(test.getShortId());
    }

    @Test
    void getCoverage_locDungTypeCoverageInformation() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);

        Warning warning = fixtures.warning(run, "DM_CONVERT_CASE", WarningTool.spotbugs, "TextNormalizer.java", 10);
        EvidenceRecord coverage = fixtures.evidenceRecord(run, EvidenceType.COVERAGE_INFORMATION, EvidenceSource.jacoco, "TextNormalizer.java", 10);
        EvidenceRecord unrelatedTest = fixtures.evidenceRecord(run, EvidenceType.TEST_RESULT, EvidenceSource.surefire, "X.java", 1);
        fixtures.link(coverage, warning.getEvidenceRecord(), EvidenceLinkRelation.COVERS, EvidenceLinkMethod.LINE_EXACT, 1.0);
        fixtures.link(unrelatedTest, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.STACK_FRAME, 1.0);

        var items = service.getCoverage(run.getId(), warning.getEvidenceRecord().getShortId());

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().shortId()).isEqualTo(coverage.getShortId());
    }

    @Test
    void searchEvidence_ilikeTrenSummary() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "PasswordHasher.java", 40);

        var results = service.searchEvidence(run.getId(), "PasswordHasher", null, null, 20);

        assertThat(results).isNotEmpty();
    }

    /**
     * Bộ lọc loại phải nằm TRONG truy vấn, không lọc sau khi đã lấy về.
     *
     * <p>Lọc sau thì {@code LIMIT} cắt trước bộ lọc: ở đây có 5 bằng chứng khớp từ khoá, trong đó
     * 2 cái đầu KHÔNG phải cảnh báo tĩnh — nên hỏi 2 cảnh báo tĩnh sẽ trả về RỖNG. Gặp thật khi
     * chạy end-to-end tuần 12-13, và với LLM thì "không tìm thấy" là một câu trả lời sai mà nó
     * tin ngay rồi kết luận `INSUFFICIENT_EVIDENCE` nhầm.
     */
    @Test
    void locTheoLoaiKhongBiLimitCatTruoc() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        fixtures.evidenceRecord(run, EvidenceType.TEST_RESULT, EvidenceSource.surefire, "UserServiceTest.java", 10);
        fixtures.evidenceRecord(run, EvidenceType.STACK_TRACE, EvidenceSource.surefire, "UserService.java", 20);
        fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "UserService.java", 30);
        fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "UserService.java", 40);
        fixtures.evidenceRecord(run, EvidenceType.CODE_CONTEXT, EvidenceSource.parser, "UserService.java", 50);

        var results = service.searchEvidence(run.getId(), "UserService", Set.of(EvidenceType.STATIC_WARNING), null, 2);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getType() == EvidenceType.STATIC_WARNING);
    }

    /**
     * Thứ tự phải ổn định giữa các lần chạy: thực nghiệm tuần 15 chạy mỗi cảnh báo 3 lần và cần
     * so sánh được các lần với nhau. Không có {@code ORDER BY} thì {@code LIMIT} trả về tập khác
     * nhau mỗi lần mà không có gì báo.
     */
    @Test
    void ketQuaTimKiemCoThuTuOnDinh() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        for (int line = 10; line <= 60; line += 10) {
            fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "CartService.java", line);
        }

        var lanMot = service.searchEvidence(run.getId(), "CartService", null, null, 3);
        var lanHai = service.searchEvidence(run.getId(), "CartService", null, null, 3);

        assertThat(lanMot).extracting(EvidenceRecord::getShortId).isEqualTo(lanHai.stream()
                .map(EvidenceRecord::getShortId)
                .toList());
    }

    @Test
    void moiToolValidateRunTruocKhiTraKetQuaRong() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun failedRun = fixtures.runWithStatus(snapshot, AnalysisRunStatus.FAILED);

        assertThatThrownBy(() -> service.searchEvidence(failedRun.getId(), "x", null, null, 20))
                .isInstanceOf(RunNotQueryableException.class);
    }
}
