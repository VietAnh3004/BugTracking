package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.service.account.AdminBootstrap;
import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.testsupport.InMemoryObjectStorageClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Trang quản trị: quyền truy cập và — quan trọng hơn — xoá một tài khoản phải xoá ĐÚNG những gì đã
 * chốt, không hơn không kém.
 *
 * <p>Nửa "không hơn" mới là nửa dễ sai: một CASCADE đặt nhầm là xoá luôn dữ liệu thực nghiệm của
 * người khác mà không có lỗi nào báo. Vì vậy test dựng cả ba người (người bị xoá, người cùng dự án,
 * chủ một dự án khác) rồi đếm hàng ở từng bảng.
 */
class AdminControllerTest extends AbstractWebIntegrationTest {

    // Khớp application-test.yml. Giá trị giả, chỉ dùng cho test.
    private static final String ADMIN_USERNAME = "admin-test";
    private static final String ADMIN_PASSWORD = "admin-test-pass-1";
    private static final String USER_PASSWORD = "mat-khau-nguoi-dung-1";

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectStorageClient objectStorageClient;

    @Autowired
    private AdminBootstrap adminBootstrap;

    private String adminToken;

    private InMemoryObjectStorageClient storage() {
        return (InMemoryObjectStorageClient) objectStorageClient;
    }

    @BeforeEach
    void dangNhapAdmin() throws Exception {
        storage().reset();
        // Đảm bảo admin còn đó: AdminBootstrapTest đổi admin trong DB dùng chung rồi khôi phục, nhưng
        // chạy lại bootstrap ở đây rẻ và khiến test này không phụ thuộc thứ tự chạy các class.
        adminBootstrap.run(new org.springframework.boot.DefaultApplicationArguments());
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    // ===================================== quyền truy cập =====================================

    @Test
    void adminDangNhapBangTenDangNhapKhongPhaiEmail() throws Exception {
        JsonNode me = read(getMockMvc()
                .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()));
        assertThat(me.get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void nguoiThuongGoiApiQuanTriNhan403() throws Exception {
        String token = registerAndLogin("thuong-" + System.nanoTime() + "@x.test");

        getMockMvc()
                .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        getMockMvc()
                .perform(delete("/api/v1/admin/users/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void chuaDangNhapNhan401() throws Exception {
        getMockMvc().perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void danhSachCoSoDuAnVaHoiThoaiVaLocDuocTheoEmail() throws Exception {
        User owner = fixtures.user("loc-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("loc-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.readySnapshotWithFiles(project, List.of("src/App.java"));
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        insertConversation(owner.getId(), run.getId());

        JsonNode users = read(getMockMvc()
                .perform(get("/api/v1/admin/users").param("q", owner.getEmail().toUpperCase())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()));

        assertThat(users).hasSize(1);
        assertThat(users.get(0).get("id").asText()).isEqualTo(owner.getId().toString());
        assertThat(users.get(0).get("ownedProjectCount").asInt()).isEqualTo(1);
        assertThat(users.get(0).get("conversationCount").asInt()).isEqualTo(1);
        // Không lộ băm mật khẩu hay định danh Google, chỉ cờ.
        assertThat(users.get(0).has("passwordHash")).isFalse();
        assertThat(users.get(0).has("googleSubject")).isFalse();
    }

    // ===================================== xoá tài khoản =====================================

    @Test
    void xoaTaiKhoanXoaDuAnCuaHoVaHoiThoaiCuaMoiNguoiTrongDoNhungGiuDuAnNguoiKhac() throws Exception {
        long stamp = System.nanoTime();
        User victim = fixtures.user("bi-xoa-" + stamp + "@x.test");
        User colleague = fixtures.user("dong-nghiep-" + stamp + "@x.test");
        User otherOwner = fixtures.user("chu-khac-" + stamp + "@x.test");

        // Dự án CỦA người bị xoá, có đồng nghiệp là thành viên và từng hỏi Agent trong đó.
        Project victimProject = fixtures.project("cua-ho-" + stamp, victim);
        RepositorySnapshot victimSnapshot = fixtures.readySnapshotWithFiles(victimProject, List.of("src/A.java"));
        AnalysisRun victimRun = fixtures.succeededRun(victimSnapshot, 1);
        Warning victimWarning = fixtures.warning(victimRun, "NP_NULL", WarningTool.spotbugs, "src/A.java", 3);
        UUID colleagueConversationInVictimProject = insertConversation(colleague.getId(), victimRun.getId());
        insertMember(victimProject.getId(), colleague.getId());
        String victimStoragePrefix = useRealStoragePrefix(victimSnapshot);
        storage().putObject(victimStoragePrefix + "src/A.java", new byte[] {1}, "text/plain");

        // Dự án CỦA NGƯỜI KHÁC, nơi người bị xoá là thành viên và đã review, hỏi Agent, gán nhãn mù.
        Project otherProject = fixtures.project("cua-nguoi-khac-" + stamp, otherOwner);
        RepositorySnapshot otherSnapshot = fixtures.readySnapshotWithFiles(otherProject, List.of("src/B.java"));
        AnalysisRun otherRun = fixtures.succeededRun(otherSnapshot, 1);
        Warning otherWarning = fixtures.warning(otherRun, "NP_NULL", WarningTool.spotbugs, "src/B.java", 4);
        insertMember(otherProject.getId(), victim.getId());
        UUID victimConversationInOtherProject = insertConversation(victim.getId(), otherRun.getId());
        insertReview(otherRun.getId(), otherWarning.getEvidenceRecordId(), victim.getId());
        insertBlindLabel(otherWarning.getEvidenceRecordId());
        String otherStoragePrefix = useRealStoragePrefix(otherSnapshot);
        storage().putObject(otherStoragePrefix + "src/B.java", new byte[] {2}, "text/plain");

        getMockMvc()
                .perform(delete("/api/v1/admin/users/{id}", victim.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // MẤT: tài khoản, dự án của họ và mọi thứ bên dưới, hội thoại của đồng nghiệp trong dự án đó.
        assertThat(count("app_user", "id", victim.getId())).isZero();
        assertThat(count("project", "id", victimProject.getId())).isZero();
        assertThat(count("repository_snapshot", "id", victimSnapshot.getId())).isZero();
        assertThat(count("analysis_run", "id", victimRun.getId())).isZero();
        assertThat(count("evidence_record", "id", victimWarning.getEvidenceRecordId())).isZero();
        assertThat(count("conversation", "id", colleagueConversationInVictimProject)).isZero();
        assertThat(count("project_member", "user_id", colleague.getId())).isZero();
        assertThat(storage().containsKey(victimStoragePrefix + "src/A.java")).isFalse();

        // MẤT: phần họ làm trên dự án người khác.
        assertThat(count("conversation", "id", victimConversationInOtherProject)).isZero();
        assertThat(count("human_review", "reviewer_id", victim.getId())).isZero();
        assertThat(count("project_member", "user_id", victim.getId())).isZero();

        // CÒN NGUYÊN: dự án người khác, đồng nghiệp, và nhãn ground truth (dữ liệu thực nghiệm).
        assertThat(count("project", "id", otherProject.getId())).isEqualTo(1);
        assertThat(count("analysis_run", "id", otherRun.getId())).isEqualTo(1);
        assertThat(count("evidence_record", "id", otherWarning.getEvidenceRecordId())).isEqualTo(1);
        assertThat(count("ground_truth_label", "evidence_record_id", otherWarning.getEvidenceRecordId()))
                .isEqualTo(1);
        assertThat(count("app_user", "id", colleague.getId())).isEqualTo(1);
        assertThat(storage().containsKey(otherStoragePrefix + "src/B.java")).isTrue();
    }

    @Test
    void khongTuXoaDuocMinh() throws Exception {
        UUID adminId = UUID.fromString(read(getMockMvc()
                        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken)))
                .get("id")
                .asText());

        getMockMvc()
                .perform(delete("/api/v1/admin/users/{id}", adminId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/admin-action-not-allowed"));
        assertThat(count("app_user", "id", adminId)).isEqualTo(1);
    }

    @Test
    void xoaTaiKhoanKhongTonTaiLa404() throws Exception {
        getMockMvc()
                .perform(delete("/api/v1/admin/users/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void nguoiBiXoaKhongDangNhapLaiDuocVaRefreshTokenMat() throws Exception {
        String email = "se-bi-xoa-" + System.nanoTime() + "@x.test";
        JsonNode tokens = registerAndGetTokens(email);
        UUID userId = UUID.fromString(tokens.at("/user/id").asText());

        getMockMvc()
                .perform(delete("/api/v1/admin/users/{id}", userId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        postJson("/api/v1/auth/refresh", "{\"refreshToken\": \"%s\"}".formatted(tokens.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, USER_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    // ===================================== helpers =====================================

    private String login(String email, String password) throws Exception {
        return read(postJson("/api/v1/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))
                        .andExpect(status().isOk()))
                .get("accessToken")
                .asText();
    }

    private String registerAndLogin(String email) throws Exception {
        return registerAndGetTokens(email).get("accessToken").asText();
    }

    /** Đăng ký xong phải đánh dấu đã xác thực email mới đăng nhập được — test không đọc thư. */
    private JsonNode registerAndGetTokens(String email) throws Exception {
        postJson("/api/v1/auth/register",
                        "{\"email\": \"%s\", \"password\": \"%s\", \"displayName\": \"Nguoi Dung\"}"
                                .formatted(email, USER_PASSWORD))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE app_user SET email_verified_at = now() WHERE lower(email) = lower(?)", email);
        return read(postJson("/api/v1/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, USER_PASSWORD))
                .andExpect(status().isOk()));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return getMockMvc().perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private int count(String table, String column, UUID value) {
        // Tên bảng/cột là hằng trong test, không phải đầu vào người dùng.
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return n == null ? 0 : n;
    }

    /**
     * Fixture đặt storage_key kiểu `test/...`, mà ProjectService chỉ dọn prefix `snapshots/<id>/` (chặn
     * prefix lạ để không xoá nhầm cả bucket) — nên đổi sang đúng quy ước thật trước khi kiểm việc dọn.
     */
    private String useRealStoragePrefix(RepositorySnapshot snapshot) {
        String prefix = "snapshots/" + snapshot.getId() + "/";
        jdbc.update("UPDATE repository_snapshot SET storage_key = ? WHERE id = ?", prefix, snapshot.getId());
        return prefix;
    }

    private UUID insertConversation(UUID ownerId, UUID runId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'phien thu')",
                id, ownerId, runId);
        return id;
    }

    private void insertMember(UUID projectId, UUID userId) {
        jdbc.update("INSERT INTO project_member (project_id, user_id) VALUES (?, ?)", projectId, userId);
    }

    private void insertReview(UUID runId, UUID evidenceRecordId, UUID reviewerId) {
        jdbc.update(
                "INSERT INTO human_review (id, analysis_run_id, evidence_record_id, reviewer_id, decision) "
                        + "VALUES (?, ?, ?, ?, 'ACCEPT')",
                UUID.randomUUID(), runId, evidenceRecordId, reviewerId);
    }

    /** Nhãn người-gán-thứ-hai: chỉ có tên hiển thị dạng chữ, không có khoá ngoại tới tài khoản. */
    private void insertBlindLabel(UUID evidenceRecordId) {
        jdbc.update(
                "INSERT INTO ground_truth_label (evidence_record_id, source, label, rationale, labeler) "
                        + "VALUES (?, 'BLIND_SECOND_RATER', 'TRUE_POSITIVE', 'co lo hong that', 'Nguoi Dung')",
                evidenceRecordId);
    }
}
