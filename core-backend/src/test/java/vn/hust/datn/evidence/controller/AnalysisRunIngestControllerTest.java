package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;
import vn.hust.datn.evidence.testsupport.TestFixtures;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

class AnalysisRunIngestControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    private User owner;
    private RepositorySnapshot snapshot;

    @BeforeEach
    void chuanBi() {
        owner = fixtures.user("ingest-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("ingest-%d".formatted(System.nanoTime()), owner);
        snapshot = fixtures.readySnapshotWithContent(project, SampleArtifacts.vulnshopSourceContents());
    }

    private MockMultipartFile part(String fileName, byte[] content) {
        return new MockMultipartFile("artifacts", fileName, "application/octet-stream", content);
    }

    private byte[] surefireZip() {
        ZipBuilder zip = new ZipBuilder();
        for (Path report : SampleArtifacts.surefireReports()) {
            zip.file("target/surefire-reports/" + report.getFileName(), SampleArtifacts.bytesOf(report));
        }
        return zip.build();
    }

    private MockMultipartHttpServletRequestBuilder boDayDu(UUID snapshotId) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/snapshots/" + snapshotId + "/analysis-runs")
                .file(part("spotbugs.xml", SampleArtifacts.spotbugsXml()))
                .file(part("semgrep.sarif", SampleArtifacts.semgrepSarif()))
                .file(part("surefire.zip", surefireZip()))
                .file(part("jacoco.xml", SampleArtifacts.jacocoXml()));
    }

    @Test
    void napBoArtifactDayDu_traVe201KemLocationVaSoLieu() throws Exception {
        int canhBaoTinh =
                SampleArtifacts.expectedInt("spotbugs", "findings") + SampleArtifacts.expectedInt("semgrep", "findings");

        getMockMvc()
                .perform(boDayDu(snapshot.getId()).with(authenticatedAs(owner)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/analysis-runs/")))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.findingCount").value(canhBaoTinh))
                .andExpect(jsonPath("$.unresolvedLocationCount").value(0))
                .andExpect(jsonPath("$.snapshotId").value(snapshot.getId().toString()))
                .andExpect(jsonPath("$.toolVersions.spotbugs").isNotEmpty())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        assertThat(analysisRunRepository.findBySnapshotIdOrderByStartedAtDesc(snapshot.getId()))
                .hasSize(1);
    }

    /** Tài nguyên không thuộc về bạn ⇒ 404, không phải 403 — và không tạo hàng nào. */
    @Test
    void napChoSnapshotKhongThuocVeMinh_traVe404() throws Exception {
        User nguoiKhac = fixtures.user("nguoi-khac-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(boDayDu(snapshot.getId()).with(authenticatedAs(nguoiKhac)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));

        assertThat(analysisRunRepository.findBySnapshotIdOrderByStartedAtDesc(snapshot.getId()))
                .isEmpty();
    }

    @Test
    void napKhiChuaDangNhap_traVe401() throws Exception {
        getMockMvc().perform(boDayDu(snapshot.getId())).andExpect(status().isUnauthorized());
    }

    /** Artifact không nhận dạng được bị từ chối TRƯỚC khi tạo bia mộ ⇒ 400, không phải một run FAILED. */
    @Test
    void napFileKhongPhaiArtifact_traVe400VaKhongTaoRun() throws Exception {
        getMockMvc()
                .perform(multipart("/api/v1/snapshots/" + snapshot.getId() + "/analysis-runs")
                        .file(part("anh.png", "khong phai artifact".getBytes(StandardCharsets.UTF_8)))
                        .with(authenticatedAs(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Artifact analyzer không hợp lệ"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("anh.png")));

        assertThat(analysisRunRepository.findBySnapshotIdOrderByStartedAtDesc(snapshot.getId()))
                .isEmpty();
    }

    /**
     * Run hỏng vì DỮ LIỆU phân tích vẫn trả 201: hàng {@code analysis_run} đã tồn tại và đọc được,
     * nên 201 mô tả đúng thực tế. FE đọc {@code status}/{@code errorMessage} để hiển thị — cùng
     * hình dạng mà giai đoạn D (chạy bất đồng bộ) sẽ dùng.
     */
    @Test
    void napThieuAnalyzerTinh_traVe201KemRunFAILEDVaLyDo() throws Exception {
        getMockMvc()
                .perform(multipart("/api/v1/snapshots/" + snapshot.getId() + "/analysis-runs")
                        .file(part("jacoco.xml", SampleArtifacts.jacocoXml()))
                        .with(authenticatedAs(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("du an sach")));
    }

    /** Phần multipart rỗng (ô chọn file chưa điền) không được biến thành lỗi nhận dạng. */
    @Test
    void phanRongBiBoQuaChuKhongLamHongYeuCau() throws Exception {
        getMockMvc()
                .perform(boDayDu(snapshot.getId())
                        .file(part("chua-chon.xml", new byte[0]))
                        .with(authenticatedAs(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }
}
