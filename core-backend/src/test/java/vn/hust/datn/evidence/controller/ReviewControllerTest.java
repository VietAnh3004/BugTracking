package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Tầng HTTP của Human Review: xác thực, phân quyền, mã lỗi.
 *
 * <p>{@link vn.hust.datn.evidence.service.review.HumanReviewServiceTest} đã phủ nghiệp vụ; ở đây
 * chỉ kiểm những gì CHỈ tầng HTTP quyết định — cùng khuôn {@link ConversationControllerTest}.
 */
class ReviewControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    private User owner;
    private AnalysisRun run;
    private String shortId;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("nguoi-xet-http-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 66);
        shortId = warning.getEvidenceRecord().getShortId();
    }

    @Test
    void ghiQuyetDinh_traVe200KemNoiDung() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\",\"note\":\"Doc ma roi, dung la loi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ACCEPT"))
                .andExpect(jsonPath("$.shortId").value(shortId))
                .andExpect(jsonPath("$.messageId").doesNotExist());
    }

    /** PUT nên gửi hai lần phải cho cùng kết quả, không tạo hai bản ghi. */
    @Test
    void guiHaiLan_vanChiMotBanGhi() throws Exception {
        for (String decision : new String[] {"ACCEPT", "REJECT"}) {
            getMockMvc()
                    .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"%s\"}".formatted(decision)))
                    .andExpect(status().isOk());
        }

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/reviews", run.getId()).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].decision").value("REJECT"));
    }

    @Test
    void khongCoJwt_traVe401KemProblemDetail() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").exists());
    }

    /**
     * Service token KHÔNG dùng được cho đường người dùng.
     *
     * <p>Đây là thao tác GHI; năm tool theo ADR 0005 đều là tool ĐỌC và đi đường riêng. Nhận cả
     * hai kiểu xác thực trên cùng endpoint là mở một cửa hậu không ai canh.
     */
    @Test
    void serviceToken_khongDungDuocChoDuongNguoiDung() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(serviceToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Quy ước phân quyền của repo: tài nguyên không thuộc về bạn ⇒ 404, không phải 403. */
    @Test
    void runCuaNguoiKhac_traVe404ChuKhongPhai403() throws Exception {
        User nguoiLa = fixtures.user("nguoi-la-" + System.nanoTime() + "@x.test");

        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(authenticatedAs(nguoiLa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void thieuDecision_traVe400KemTenTruong() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"quen chon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.decision").exists());
    }

    /** Giá trị lạ ngoài bốn lựa chọn phải ra 400 đọc được, không phải 500. */
    @Test
    void decisionKhongHopLe_traVe400() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CHAC_LA_DUNG\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 0 giây là dữ liệu rác — chặn ở tầng DTO để không phải chờ CHECK constraint của DB. */
    @Test
    void thoiGianBangKhong_traVe400() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\",\"secondsSpent\":0}"))
                .andExpect(status().isBadRequest());
    }

    /** messageId bịa ⇒ 400 `invalid-review`, KHÔNG lặng lẽ ghi NULL rồi đẩy vào nhóm đối chứng. */
    @Test
    void messageIdBia_traVe400InvalidReview() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), shortId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\",\"messageId\":\"11111111-2222-3333-4444-555555555555\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/invalid-review"));
    }

    @Test
    void canhBaoKhongTonTai_traVe404() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/review", run.getId(), "EV-FFFF")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isNotFound());
    }
}
