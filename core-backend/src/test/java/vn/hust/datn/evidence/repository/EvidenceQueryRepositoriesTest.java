package vn.hust.datn.evidence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
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
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Ngày 2 của tuần 4: xác nhận TestFixtures dựng đủ chuỗi project→snapshot→run→evidence→warning→
 * link, và các query method mới (WarningView projection, EvidenceLink hai bước) hoạt động đúng
 * trên dữ liệu thật — không chỉ biên dịch được.
 */
class EvidenceQueryRepositoriesTest extends AbstractIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private EvidenceRecordRepository evidenceRecordRepository;

    @Autowired
    private WarningRepository warningRepository;

    @Autowired
    private EvidenceLinkRepository evidenceLinkRepository;

    @Test
    void chuoiFixtureDayDuVaWarningViewPhangDungGiaTri() {
        Project project = fixtures.project("vulnshop-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);

        Warning warning = fixtures.warning(run, "WEAK_MESSAGE_DIGEST_MD5", WarningTool.spotbugs, "PasswordHasher.java", 40);

        assertThat(warning.getEvidenceRecordId()).isNotNull();

        WarningView view = warningRepository
                .findFlatByRunAndShortId(run.getId(), warning.getEvidenceRecord().getShortId())
                .orElseThrow();

        assertThat(view.filePath()).isEqualTo("PasswordHasher.java");
        assertThat(view.startLine()).isEqualTo(40);
        assertThat(view.ruleId()).isEqualTo("WEAK_MESSAGE_DIGEST_MD5");
        assertThat(view.tool()).isEqualTo(WarningTool.spotbugs);
    }

    @Test
    void existsByShortIdPhanBietDuocKhongTonTaiVaTonTai() {
        assertThat(evidenceRecordRepository.existsByShortId("EV-KHONG-TON-TAI")).isFalse();

        Project project = fixtures.project("vulnshop-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "SQL_INJECTION_SPRING_JDBC", WarningTool.spotbugs, "UserRepository.java", 35);

        assertThat(evidenceRecordRepository.existsByShortId(warning.getEvidenceRecord().getShortId())).isTrue();
    }

    @Test
    void evidenceLinkHaiBuocGomDuocEvidenceDoiDien() {
        Project project = fixtures.project("vulnshop-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);

        EvidenceRecord warningEvidence = fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "CartService.java", 25);
        EvidenceRecord testEvidence = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "CartServiceTest.java", 37);

        fixtures.link(testEvidence, warningEvidence, EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.STACK_FRAME, 0.9);

        var links = evidenceLinkRepository.findByFromEvidenceIdOrToEvidenceId(warningEvidence.getId(), warningEvidence.getId());
        assertThat(links).hasSize(1);
        assertThat(links.getFirst().getMethod()).isEqualTo(EvidenceLinkMethod.STACK_FRAME);

        var oppositeIds = links.stream()
                .map(l -> l.getFromEvidenceId().equals(warningEvidence.getId()) ? l.getToEvidenceId() : l.getFromEvidenceId())
                .toList();
        var opposite = evidenceRecordRepository.findAllById(oppositeIds);
        assertThat(opposite).hasSize(1);
        assertThat(opposite.getFirst().getId()).isEqualTo(testEvidence.getId());
    }

    @Test
    void findByAnalysisRunIdAndTypePhanTrangDung() {
        Project project = fixtures.project("vulnshop-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 3);
        fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "A.java", 1);
        fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "B.java", 2);
        fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "ATest.java", 10);

        var page = evidenceRecordRepository.findByAnalysisRunIdAndType(run.getId(), EvidenceType.STATIC_WARNING, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
