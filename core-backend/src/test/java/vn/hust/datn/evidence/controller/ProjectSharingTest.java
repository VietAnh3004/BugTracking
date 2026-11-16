package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Chia sẻ dự án (V10): thành viên ĐỌC được và làm được đúng những gì đã chốt (xem, hỏi Agent,
 * review, gán nhãn mù) — và KHÔNG làm được mọi thao tác dành cho chủ dự án.
 *
 * <p>Nửa thứ hai mới là nửa quan trọng: mở rộng quyền đọc là đụng vào mọi truy vấn phân quyền,
 * và một chỗ đổi nhầm từ "chỉ chủ" sang "đọc được" là một lỗ hổng không có triệu chứng nào.
 * Mỗi thao tác chỉ-chủ có một assert riêng ở đây.
 */
class ProjectSharingTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    private User owner;
    private User member;
    private User stranger;
    private Project project;
    private RepositorySnapshot snapshot;
    private AnalysisRun run;
    private String warningShortId;

    @BeforeEach
    void setUp() throws Exception {
        owner = fixtures.user("chu-" + System.nanoTime() + "@x.test");
        member = fixtures.user("thanh-vien-" + System.nanoTime() + "@x.test");
        stranger = fixtures.user("nguoi-la-" + System.nanoTime() + "@x.test");
        project = fixtures.project("chia-se-" + System.nanoTime(), owner);
        snapshot = fixtures.readySnapshotWithFiles(project, List.of("src/App.java"));
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "src/App.java", 3);
        warningShortId = warning.getEvidenceRecord().getShortId();

        getMockMvc()
                .perform(post("/api/v1/projects/{id}/members", project.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(member.getEmail()));
    }

    @Test
    void thanhVienThayDuAnTrongDanhSach_kemVaiTroMember() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects").with(authenticatedAs(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')].role".formatted(project.getId()))
                        .value("MEMBER"));
        getMockMvc()
                .perform(get("/api/v1/projects/{id}", project.getId()).with(authenticatedAs(owner)))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void thanhVienDocDuocSnapshotCanhBaoVaBangChung() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects/{id}/snapshots", project.getId()).with(authenticatedAs(member)))
                .andExpect(status().isOk());
        getMockMvc()
                .perform(get("/api/v1/snapshots/{id}/files", snapshot.getId()).with(authenticatedAs(member)))
                .andExpect(status().isOk());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{id}/warnings", run.getId()).with(authenticatedAs(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/evidence/{shortId}", run.getId(), warningShortId)
                        .with(authenticatedAs(member)))
                .andExpect(status().isOk());
    }

    /** Hỏi Agent và review là hai việc thành viên được phép (chốt với người dùng). */
    @Test
    void thanhVienMoHoiThoaiVaReviewDuoc() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        getMockMvc()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), warningShortId)
                        .with(authenticatedAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk());
    }

    /** Mọi thao tác chỉ dành cho chủ dự án: thành viên nhận 404 như người lạ, không phải 403. */
    @Test
    void thanhVienKhongLamDuocThaoTacCuaChuDuAn() throws Exception {
        getMockMvc()
                .perform(delete("/api/v1/projects/{id}", project.getId()).with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/projects/{id}/members", project.getId()).with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(post("/api/v1/projects/{id}/members", project.getId())
                        .with(authenticatedAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(stranger.getEmail())))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(multipart("/api/v1/projects/{id}/snapshots", project.getId())
                        .file(new MockMultipartFile("file", "x.zip", "application/zip", new byte[] {1}))
                        .with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(multipart("/api/v1/snapshots/{id}/analysis-runs", snapshot.getId())
                        .file(new MockMultipartFile("artifacts", "spotbugs.xml", "text/xml", new byte[] {1}))
                        .with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{id}/links/rebuild", run.getId()).with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{id}/metrics", run.getId()).with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{id}/export.json", run.getId()).with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{id}/ground-truth/export.yaml", run.getId())
                        .with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nguoiLaVanKhongThayGi() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects/{id}", project.getId()).with(authenticatedAs(stranger)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{id}/warnings", run.getId()).with(authenticatedAs(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void goThanhVien_matQuyenNgay() throws Exception {
        getMockMvc()
                .perform(delete("/api/v1/projects/{id}/members/{userId}", project.getId(), member.getId())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isNoContent());

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{id}/warnings", run.getId()).with(authenticatedAs(member)))
                .andExpect(status().isNotFound());
    }

    @Test
    void moiEmailChuaDangKyHoacMoiChinhMinh_400KemLyDo() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/projects/{id}/members", project.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"chua-dang-ky-%d@x.test\"}".formatted(System.nanoTime())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-member")));
        getMockMvc()
                .perform(post("/api/v1/projects/{id}/members", project.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(owner.getEmail())))
                .andExpect(status().isBadRequest());
    }

    /** Mời lại người đã là thành viên: không lỗi, danh sách không nhân đôi. */
    @Test
    void moiLaiLaIdempotent() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/projects/{id}/members", project.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(member.getEmail().toUpperCase())))
                .andExpect(status().isOk());

        getMockMvc()
                .perform(get("/api/v1/projects/{id}/members", project.getId()).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
