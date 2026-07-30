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

    @Test
    void moiToolValidateRunTruocKhiTraKetQuaRong() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun failedRun = fixtures.runWithStatus(snapshot, AnalysisRunStatus.FAILED);

        assertThatThrownBy(() -> service.searchEvidence(failedRun.getId(), "x", null, null, 20))
                .isInstanceOf(RunNotQueryableException.class);
    }
}
