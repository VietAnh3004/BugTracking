package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;
import vn.hust.datn.evidence.testsupport.TestFixtures;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

/** Ba endpoint đọc mà màn hình cảnh báo (giai đoạn C) sẽ gọi. */
class AnalysisRunQueryControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User owner;
    private RepositorySnapshot snapshot;
    private UUID runId;

    @BeforeEach
    void napMotRunThat() throws Exception {
        owner = fixtures.user("query-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("query-%d".formatted(System.nanoTime()), owner);
        snapshot = fixtures.readySnapshotWithContent(project, SampleArtifacts.vulnshopSourceContents());

        ZipBuilder zip = new ZipBuilder();
        for (Path report : SampleArtifacts.surefireReports()) {
            zip.file("target/surefire-reports/" + report.getFileName(), SampleArtifacts.bytesOf(report));
        }
        String body = getMockMvc()
                .perform(multipart("/api/v1/snapshots/" + snapshot.getId() + "/analysis-runs")
                        .file(new MockMultipartFile("artifacts", "spotbugs.xml", null, SampleArtifacts.spotbugsXml()))
                        .file(new MockMultipartFile("artifacts", "semgrep.sarif", null, SampleArtifacts.semgrepSarif()))
                        .file(new MockMultipartFile("artifacts", "surefire.zip", null, zip.build()))
                        .file(new MockMultipartFile("artifacts", "jacoco.xml", null, SampleArtifacts.jacocoXml()))
                        .with(authenticatedAs(owner)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        runId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    @Test
    void danhSachRunCuaSnapshot_traVeRunVuaNap() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshot.getId() + "/analysis-runs").with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(runId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"));
    }

    @Test
    void chiTietRun_traVeBoDemDuongDan() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runId).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationCount").isNumber())
                .andExpect(jsonPath("$.unresolvedLocationCount").value(0));
    }

    /**
     * Endpoint chi tiết CỐ Ý đọc được run {@code FAILED} — đó là chỗ duy nhất FE lấy được
     * {@code errorMessage} để hiển thị thay vì một màn hình trống.
     */
    @Test
    void chiTietRunFAILED_vanDocDuocKemLyDo() throws Exception {
        UUID runHong = UUID.fromString(objectMapper
                .readTree(getMockMvc()
                        .perform(multipart("/api/v1/snapshots/" + snapshot.getId() + "/analysis-runs")
                                .file(new MockMultipartFile("artifacts", "jacoco.xml", null, SampleArtifacts.jacocoXml()))
                                .with(authenticatedAs(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText());

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runHong).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty());
    }

    /**
     * Danh sách cảnh báo NGƯỢC LẠI: run chưa truy vấn được thì trả 409, không trả danh sách rỗng —
     * "rỗng vì chưa xong" và "rỗng vì thật sự rỗng" phải phân biệt được.
     */
    @Test
    void danhSachCanhBaoCuaRunFAILED_traVe409ChuKhongPhaiDanhSachRong() throws Exception {
        var runDangChay = fixtures.runWithStatus(snapshot, AnalysisRunStatus.RUNNING);

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runDangChay.getId() + "/warnings").with(authenticatedAs(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Run chưa thể truy vấn"));
    }

    /**
     * Chốt chặn của cả endpoint này: sắp theo {@code severity_rank}, KHÔNG theo
     * {@code severity_normalized}. Sắp theo chữ cái cho ra HIGH, INFO, LOW, MEDIUM — vẫn có HIGH
     * đứng đầu nên nhìn qua không thấy sai.
     */
    @Test
    void danhSachCanhBao_sapTheoMucNghiemTrongChuKhongPhaiChuCai() throws Exception {
        String body = getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runId + "/warnings")
                        .param("size", "100")
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total")
                        .value(SampleArtifacts.expectedInt("spotbugs", "findings")
                                + SampleArtifacts.expectedInt("semgrep", "findings")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(body).get("items");
        int truoc = -1;
        boolean coHonMotMuc = false;
        for (JsonNode item : items) {
            int rank = thuTu(item.get("severityNormalized").asText());
            Assertions.assertThat(rank).isGreaterThanOrEqualTo(truoc);
            if (truoc >= 0 && rank != truoc) {
                coHonMotMuc = true;
            }
            truoc = rank;
        }
        // Nếu chỉ có một mức nghiêm trọng thì assert ở trên xanh vô nghĩa.
        Assertions.assertThat(coHonMotMuc).isTrue();
    }

    private int thuTu(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            default -> 3;
        };
    }

    @Test
    void danhSachCanhBao_locDuocTheoToolVaTheoFile() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runId + "/warnings")
                        .param("tool", "semgrep")
                        .param("size", "100")
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(SampleArtifacts.expectedInt("semgrep", "findings")))
                .andExpect(jsonPath("$.items[?(@.tool != 'semgrep')]").isEmpty());

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runId + "/warnings")
                        .param("filePath", "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java")
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    /** Không thuộc về bạn ⇒ 404 ở cả ba endpoint, không phải 403. */
    @Test
    void nguoiKhacTruyCap_traVe404OCaBaEndpoint() throws Exception {
        User nguoiKhac = fixtures.user("khac-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshot.getId() + "/analysis-runs").with(authenticatedAs(nguoiKhac)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runId).with(authenticatedAs(nguoiKhac)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + runId + "/warnings").with(authenticatedAs(nguoiKhac)))
                .andExpect(status().isNotFound());
    }

    @Test
    void runKhongTonTai_traVe404() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/" + UUID.randomUUID()).with(authenticatedAs(owner)))
                .andExpect(status().isNotFound());
    }
}
