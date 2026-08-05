package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;
import vn.hust.datn.evidence.service.analysis.sandbox.SandboxRunner;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Endpoint chạy sandbox ở cấu hình MẶC ĐỊNH — tức sandbox TẮT.
 *
 * <p>Đây là trạng thái mà phần lớn môi trường sẽ ở: {@code app.sandbox.runner=disabled} theo mặc
 * định, và deploy lên máy chủ không có Docker daemon vẫn phải chạy được mọi thứ khác. Test này
 * canh đúng lời hứa đó — nếu một ngày bean sandbox trở thành bắt buộc, cả ứng dụng sẽ không khởi
 * động và ở đây sẽ đỏ trước tiên.
 *
 * <p>Đường chạy sandbox THẬT được kiểm ở {@code DockerSandboxRunnerTest} (chạy container thật).
 */
class SandboxRunControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Autowired
    private SandboxRunner sandboxRunner;

    private record SnapshotAndOwner(RepositorySnapshot snapshot, User owner) {}

    private SnapshotAndOwner snapshotHopLe() {
        User owner = fixtures.user("sandbox-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("sandbox-%d".formatted(System.nanoTime()), owner);
        RepositorySnapshot snapshot = fixtures.readySnapshotWithContent(
                project,
                Map.of(
                        "pom.xml", "<project><artifactId>demo</artifactId></project>",
                        "src/main/java/A.java", "class A {}"));
        return new SnapshotAndOwner(snapshot, owner);
    }

    @Test
    void macDinhSandboxTat_beanLaDisabledRunnerVaUngDungVanKhoiDong() {
        assertThat(sandboxRunner.isAvailable()).isFalse();
    }

    /**
     * 503 chứ không phải 500, và KHÔNG tạo hàng {@code analysis_run} nào — không để lại bia mộ cho
     * một việc còn chưa bắt đầu được.
     */
    @Test
    void chaySandboxKhiDangTat_traVe503VaKhongTaoRun() throws Exception {
        SnapshotAndOwner fixture = snapshotHopLe();

        getMockMvc()
                .perform(post("/api/v1/snapshots/" + fixture.snapshot().getId() + "/analysis-runs/sandbox")
                        .with(authenticatedAs(fixture.owner())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Sandbox không dùng được"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("nap artifact thu cong")));

        assertThat(analysisRunRepository.findBySnapshotIdOrderByStartedAtDesc(
                        fixture.snapshot().getId()))
                .isEmpty();
    }

    /** Tài nguyên không thuộc về bạn ⇒ 404, và kiểm đó phải chạy TRƯỚC kiểm sandbox có bật không. */
    @Test
    void chaySandboxTrenSnapshotKhongThuocVeMinh_traVe404ChuKhongPhai503() throws Exception {
        SnapshotAndOwner fixture = snapshotHopLe();
        User nguoiKhac = fixtures.user("khac-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(post("/api/v1/snapshots/" + fixture.snapshot().getId() + "/analysis-runs/sandbox")
                        .with(authenticatedAs(nguoiKhac)))
                .andExpect(status().isNotFound());
    }

    @Test
    void chaySandboxKhiChuaDangNhap_traVe401() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/snapshots/" + UUID.randomUUID() + "/analysis-runs/sandbox"))
                .andExpect(status().isUnauthorized());
    }

}
// Cây mã nguồn không phân tích được (Gradle, multi-module, sha256 lệch) được canh ở
// SourceTreeBuilderTest; ánh xạ sang HTTP 409 nằm ở GlobalExceptionHandler. Không kiểm lại qua
// endpoint này vì ở cấu hình mặc định, phép kiểm sandbox-có-bật-không chạy TRƯỚC nên nhánh 409
// không với tới được — một test "đi qua" đường đó sẽ chỉ chứng minh 503, tức là xanh vì lý do sai.
