package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Tầng HTTP của hội thoại: xác thực, phân quyền, và mã lỗi.
 *
 * <p>{@link vn.hust.datn.evidence.service.ConversationFlowTest} đã phủ luồng nghiệp vụ; ở đây chỉ
 * kiểm những thứ CHỈ tầng HTTP quyết định — và chúng là những thứ không test nào khác chạm tới:
 * thiếu JWT ra 401 chứ không phải 500, tài nguyên của người khác ra 404 chứ không phải 403, và
 * đầu vào sai khuôn ra 400 theo đúng khuôn ProblemDetail của dự án.
 */
class ConversationControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    private User owner;
    private AnalysisRun run;
    private String warningShortId;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("chu-hoi-thoai-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 66);
        warningShortId = warning.getEvidenceRecord().getShortId();
    }

    @Test
    void moPhien_traVe201KemNoiDung() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"focusShortId\":\"%s\"}".formatted(warningShortId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.focusShortId").value(warningShortId))
                .andExpect(jsonPath("$.analysisRunId").value(run.getId().toString()));
    }

    /** Thiếu JWT phải ra 401 JSON, KHÔNG phải trang lỗi mặc định của Spring Security. */
    @Test
    void khongCoJwt_traVe401KemProblemDetail() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").exists());
    }

    /**
     * Trình duyệt tuyệt đối không được gọi được đường này bằng service token.
     *
     * <p>Đây là thao tác GHI của người dùng; năm tool theo ADR 0005 đều là tool ĐỌC và đi đường
     * riêng. Nhận cả hai kiểu xác thực trên cùng một endpoint là mở một cửa hậu không ai canh.
     */
    @Test
    void serviceToken_khongDungDuocChoDuongNguoiDung() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(serviceToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /** Quy ước phân quyền của repo: tài nguyên không thuộc về bạn ⇒ 404, không phải 403. */
    @Test
    void runCuaNguoiKhac_traVe404ChuKhongPhai403() throws Exception {
        User nguoiLa = fixtures.user("nguoi-la-" + System.nanoTime() + "@x.test");

        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(nguoiLa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void focusShortIdSaiKhuonDang_traVe400KemTenTruong() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"focusShortId\":\"khong-phai-id\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.focusShortId").exists());
    }

    /** Hỏi về một run đang chạy là vô nghĩa: mọi Evidence ID còn chưa tồn tại. */
    @Test
    void runChuaTruyVanDuoc_traVe409() throws Exception {
        RepositorySnapshot snapshot = fixtures.snapshot(fixtures.project("p2-" + System.nanoTime(), owner));
        AnalysisRun dangChay = fixtures.runWithStatus(snapshot, AnalysisRunStatus.RUNNING);

        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", dangChay.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void danhSachPhien_chiThayPhienCuaMinh() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        User nguoiLa = fixtures.user("nguoi-la2-" + System.nanoTime() + "@x.test");

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(nguoiLa)))
                .andExpect(status().isNotFound());
    }

    @Test
    void phienKhongTonTai_traVe404() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cauHoiRong_traVe400() throws Exception {
        String conversationId = getMockMvc()
                .perform(post("/api/v1/analysis-runs/{runId}/conversations", run.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        getMockMvc()
                .perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
