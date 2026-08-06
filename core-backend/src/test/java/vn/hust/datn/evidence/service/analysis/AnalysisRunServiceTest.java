package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.exception.SnapshotNotReadyException;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;
import vn.hust.datn.evidence.testsupport.TestFixtures;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

/**
 * Đường đi đầy đủ của deliverable tuần 8-9: <b>một lệnh → warning và kết quả test nằm trong DB</b>.
 *
 * <p>Chạy trên artifact THẬT của vulnshop và trên cây file THẬT của nó, nên nó cũng là chỗ chứng
 * minh bốn parser + chuẩn hoá đường dẫn + materializer ghép lại với nhau đúng.
 */
class AnalysisRunServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AnalysisRunService analysisRunService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private RepositorySnapshot snapshot;

    @BeforeEach
    void chuanBiSnapshotDayDu() {
        User owner = fixtures.user("analysis-" + UUID.randomUUID() + "@test.local");
        Project project = fixtures.project("analysis-" + UUID.randomUUID(), owner);
        ownerId = owner.getId();
        snapshot = fixtures.readySnapshotWithContent(project, SampleArtifacts.vulnshopSourceContents());
    }

    private NamedArtifact surefireZip() {
        ZipBuilder zip = new ZipBuilder();
        for (Path report : SampleArtifacts.surefireReports()) {
            zip.file(
                    "target/surefire-reports/" + report.getFileName(),
                    SampleArtifacts.bytesOf(report));
        }
        return new NamedArtifact("surefire.zip", zip.build());
    }

    private List<NamedArtifact> boDayDu() {
        return new ArrayList<>(List.of(
                new NamedArtifact("spotbugs.xml", SampleArtifacts.spotbugsXml()),
                new NamedArtifact("semgrep.sarif", SampleArtifacts.semgrepSarif()),
                surefireZip(),
                new NamedArtifact("jacoco.xml", SampleArtifacts.jacocoXml())));
    }

    private int countEvidence(UUID runId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence_record WHERE analysis_run_id = ?", Integer.class, runId);
    }

    private int countRunsOfSnapshot() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analysis_run WHERE snapshot_id = ?", Integer.class, snapshot.getId());
    }

    @Test
    void ingest_boArtifactDayDu_runSUCCEEDED_kemDuSoLieu() {
        AnalysisRun run = analysisRunService.ingest(snapshot.getId(), ownerId, boDayDu());

        int canhBaoTinh =
                SampleArtifacts.expectedInt("spotbugs", "findings") + SampleArtifacts.expectedInt("semgrep", "findings");
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(run.getFindingCount()).isEqualTo(canhBaoTinh);
        assertThat(run.getErrorMessage()).isNull();
        assertThat(run.getFinishedAt()).isNotNull();

        // 90 evidence TỪ ARTIFACT (28 SpotBugs + 5 Semgrep + 33 test + 5 stack trace + 19 coverage).
        // Đếm riêng CODE_CONTEXT vì nó không đến từ artifact mà từ JavaParser đọc mã nguồn — trộn
        // vào một con số thì mọi lần chỉnh heuristic của extractor lại làm đỏ một test vốn canh
        // parser artifact.
        assertThat(countEvidence(run.getId()) - run.getCodeContextCount()).isEqualTo(90);

        // Tiêu chí nghiệm thu của kế hoạch: 0 vị trí hỏng trên cây nguồn thật.
        assertThat(run.getLocationCount()).isPositive();
        assertThat(run.getUnresolvedLocationCount()).isZero();

        // Assert theo TÍNH CHẤT, không theo số tuyệt đối: heuristic của JavaParser và của linker
        // sẽ còn sửa, nhưng "có cảnh báo đối chiếu được thì phải có ngữ cảnh và phải có liên kết"
        // là bất biến — chính là thứ ck_run_links_not_silently_empty ép ở tầng DB.
        assertThat(run.getCodeContextCount()).isPositive();
        assertThat(run.getLinkCount()).isPositive();
        assertThat(run.getLinkCountsByMethod()).isNotEmpty();
    }

    /** JaCoCo không khai phiên bản — khoá vắng mặt là bình thường, không phải thiếu dữ liệu. */
    @Test
    void ingest_toolVersionsDocTuChinhArtifact_vaKhongCoKhoaJacoco() {
        AnalysisRun run = analysisRunService.ingest(snapshot.getId(), ownerId, boDayDu());

        assertThat(run.getToolVersions()).containsKeys("spotbugs", "semgrep", "surefire").doesNotContainKey("jacoco");
        assertThat(run.getToolVersions().values()).allSatisfy(version -> assertThat((String) version)
                .isNotBlank());
    }

    /**
     * Thứ tự nạp là CỐ ĐỊNH, không phụ thuộc thứ tự người dùng gửi lên — vì nó trở thành
     * {@code seq_in_run}, tức trở thành Evidence ID mà LLM trích dẫn.
     */
    @Test
    void ingest_guiNguocThuTu_seqInRunVanTheoThuTuChuan() {
        List<NamedArtifact> nguoc = boDayDu();
        java.util.Collections.reverse(nguoc);

        AnalysisRun run = analysisRunService.ingest(snapshot.getId(), ownerId, nguoc);

        List<String> nguon = jdbcTemplate.queryForList(
                "SELECT source FROM evidence_record WHERE analysis_run_id = ? ORDER BY seq_in_run",
                String.class,
                run.getId());
        // 'parser' đứng cuối vì CODE_CONTEXT được nối vào SAU cùng, khi đã biết vị trí nào đối
        // chiếu được — nó không phải một artifact người dùng gửi lên nên không nằm trong thứ tự
        // nạp chuẩn của AnalyzerRegistry.
        assertThat(nguon).startsWith("spotbugs").endsWith("parser");
        assertThat(nguon.stream().distinct())
                .containsExactly("spotbugs", "semgrep", "surefire", "jacoco", "parser");
    }

    @Test
    void ingest_snapshotKhongThuocVeNguoiGoi_baoKhongTonTaiVaKhongTaoRun() {
        assertThatThrownBy(() -> analysisRunService.ingest(snapshot.getId(), UUID.randomUUID(), boDayDu()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(countRunsOfSnapshot()).isZero();
    }

    @Test
    void ingest_snapshotChuaReady_tuChoiVaKhongTaoRun() {
        Project project = fixtures.project("chua-ready-" + UUID.randomUUID());
        RepositorySnapshot pending = fixtures.snapshot(project);
        UUID chuSoHuu = jdbcTemplate.queryForObject(
                "SELECT owner_id FROM project WHERE id = ?", UUID.class, project.getId());

        assertThatThrownBy(() -> analysisRunService.ingest(pending.getId(), chuSoHuu, boDayDu()))
                .isInstanceOf(SnapshotNotReadyException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM analysis_run WHERE snapshot_id = ?", Integer.class, pending.getId()))
                .isZero();
    }

    /**
     * Không để lại bia mộ cho mọi lần gửi sai định dạng — cùng lý do {@code SnapshotService} gọi
     * {@code validateFormat} trước khi tạo hàng PENDING.
     */
    @Test
    void ingest_artifactKhongNhanDangDuoc_nemTruocKhiTaoBiaMo() {
        List<NamedArtifact> co1FileLa = boDayDu();
        co1FileLa.add(new NamedArtifact("anh.png", "khong phai artifact".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> analysisRunService.ingest(snapshot.getId(), ownerId, co1FileLa))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("anh.png");

        assertThat(countRunsOfSnapshot()).isZero();
    }

    @Test
    void ingest_khongCoArtifactNao_nem() {
        assertThatThrownBy(() -> analysisRunService.ingest(snapshot.getId(), ownerId, List.of()))
                .isInstanceOf(InvalidArtifactException.class);
        assertThat(countRunsOfSnapshot()).isZero();
    }

    /**
     * Chỉ có JaCoCo thì {@code finding_count} bằng 0, nhưng gọi đó là "dự án sạch" là nói dối: ta
     * chưa hề chạy công cụ nào có thể sinh cảnh báo.
     */
    @Test
    void ingest_khongCoAnalyzerTinhNao_runFAILEDChuKhongPhaiSUCCEEDED_CLEAN() {
        AnalysisRun run = analysisRunService.ingest(
                snapshot.getId(), ownerId, List.of(new NamedArtifact("jacoco.xml", SampleArtifacts.jacocoXml())));

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("khong the ket luan du an sach");
        assertThat(run.getCleanAckNote()).isNull();
        // Bằng chứng ĐÃ ghi vẫn còn nguyên: đó là thứ để người vận hành tra vì sao run hỏng.
        assertThat(countEvidence(run.getId())).isEqualTo(19);
    }

    /**
     * Artifact sinh từ một mã nguồn KHÁC với snapshot: 100% vị trí không đối chiếu được. Đây là
     * rủi ro mà ADR 0004 gọi là lớn nhất còn lại — hỏng kiểu này cho ra một Evidence Store trông
     * đầy đủ nhưng không bằng chứng nào bấm được.
     */
    @Test
    void ingest_toanBoDuongDanKhongKhopSnapshot_runFAILED() {
        Project project = fixtures.project("khac-ma-nguon-" + UUID.randomUUID());
        RepositorySnapshot khac = fixtures.readySnapshotWithFiles(project, List.of("README.md", "src/main/js/app.js"));
        UUID chuSoHuu =
                jdbcTemplate.queryForObject("SELECT owner_id FROM project WHERE id = ?", UUID.class, project.getId());

        AnalysisRun run = analysisRunService.ingest(khac.getId(), chuSoHuu, boDayDu());

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("Khong doi chieu duoc vi tri nao");
        assertThat(run.getUnresolvedLocationCount()).isEqualTo(run.getLocationCount());
        assertThat(run.getLocationCount()).isPositive();
    }

    /**
     * Artifact là dữ liệu KHÔNG TIN CẬY: hỏng giữa chừng phải cho run FAILED kèm lý do đọc được,
     * không phải một ngoại lệ thoát ra thành HTTP 500.
     */
    @Test
    void ingest_artifactHongGiuaChung_runFAILEDKemTenFileVaLyDo() {
        List<NamedArtifact> coFileHong = new ArrayList<>(List.of(
                new NamedArtifact("spotbugs.xml", SampleArtifacts.spotbugsXml()),
                new NamedArtifact("TEST-rong.xml", "<testsuite name=\"a.B\"/>".getBytes(StandardCharsets.UTF_8))));

        AnalysisRun run = analysisRunService.ingest(snapshot.getId(), ownerId, coFileHong);

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("TEST-rong.xml").contains("khong co testcase nao");
    }

    /**
     * SpotBugs 0 cảnh báo hiện bị {@code SpotbugsXmlAdapter} từ chối ngay ở tầng parser (nguyên tắc
     * "0 cảnh báo phải bị coi là lỗi"), nên run ra FAILED chứ KHÔNG phải {@code SUCCEEDED_CLEAN}.
     *
     * <p>Hệ quả: nhánh {@code SUCCEEDED_CLEAN} của {@code decide()} chưa thể chạm tới ở giai đoạn
     * này. Nó dành cho giai đoạn D, nơi bốn tầng kiểm tính tỉnh táo (canary, tổng số class đã
     * quét, phiên bản JDK, plugin đã nạp) mới đủ dữ kiện để phân biệt "dự án sạch thật" với
     * "analyzer im lặng hỏng" — điều mà một artifact do người dùng tự tải lên không cho biết.
     */
    @Test
    void ingest_spotbugsKhongCoCanhBaoNao_hienGioRaFAILEDChuKhongPhaiCLEAN() {
        byte[] rong = "<BugCollection version=\"4.9.6\"><BugInstance type=\"X\"/></BugCollection>"
                .replace("<BugInstance type=\"X\"/>", "")
                .getBytes(StandardCharsets.UTF_8);

        AnalysisRun run = analysisRunService.ingest(
                snapshot.getId(), ownerId, List.of(new NamedArtifact("spotbugs.xml", rong)));

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(run.getCleanAckNote()).isNull();
    }
}
