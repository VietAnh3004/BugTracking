package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Đổi tên và xoá phiên hỏi Agent.
 *
 * <p>Điều đáng khoá nhất là nửa "không hơn": xoá phiên chỉ ẨN, còn message và {@code llm_trace} phải
 * nằm nguyên trong DB — đó là số liệu thực nghiệm không tái tạo được.
 */
class ConversationManageTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private AnalysisRun run;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("chu-phien-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        conversationId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'Phien cu')",
                conversationId,
                owner.getId(),
                run.getId());
        UUID messageId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO message (id, conversation_id, ordinal, role, content) VALUES (?, ?, 0, 'USER', 'hoi')",
                messageId,
                conversationId);
        jdbc.update(
                "INSERT INTO llm_trace (id, conversation_id, message_id, iteration, provider, model, prompt_version)"
                        + " VALUES (?, ?, ?, 0, 'scripted', 'model-gia', 'v1')",
                UUID.randomUUID(),
                conversationId,
                messageId);
    }

    private ResultActions rename(User who, UUID id, String body) throws Exception {
        return getMockMvc()
                .perform(patch("/api/v1/conversations/{id}", id)
                        .with(authenticatedAs(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions remove(User who, UUID id) throws Exception {
        return getMockMvc().perform(delete("/api/v1/conversations/{id}", id).with(authenticatedAs(who)));
    }

    private ResultActions listFor(User who) throws Exception {
        return getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/conversations", run.getId()).with(authenticatedAs(who)));
    }

    private int count(String table, UUID conversation) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE conversation_id = ?", Integer.class, conversation);
        return n == null ? 0 : n;
    }

    // ===================================== đổi tên =====================================

    @Test
    void doiTenCapNhatTieuDeVaCatKhoangTrang() throws Exception {
        rename(owner, conversationId, "{\"title\":\"  Ten moi cua phien  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.title").value("Ten moi cua phien"));

        listFor(owner).andExpect(jsonPath("$[0].title").value("Ten moi cua phien"));
    }

    @Test
    void doiTenKhongLamPhienNhayLenDauDanhSach() throws Exception {
        // Danh sách sắp theo lần hoạt động gần nhất; đổi tên không phải hoạt động hỏi đáp.
        jdbc.update("UPDATE conversation SET updated_at = now() - interval '1 day' WHERE id = ?", conversationId);
        UUID newer = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'Phien moi hon')",
                newer,
                owner.getId(),
                run.getId());

        rename(owner, conversationId, "{\"title\":\"Doi ten phien cu\"}").andExpect(status().isOk());

        listFor(owner)
                .andExpect(jsonPath("$[0].id").value(newer.toString()))
                .andExpect(jsonPath("$[1].id").value(conversationId.toString()));
    }

    @Test
    void doiTenVoiTieuDeRongHoacQuaDaiBi400() throws Exception {
        rename(owner, conversationId, "{\"title\":\"   \"}").andExpect(status().isBadRequest());
        rename(owner, conversationId, "{}").andExpect(status().isBadRequest());
        rename(owner, conversationId, "{\"title\":\"" + "x".repeat(201) + "\"}").andExpect(status().isBadRequest());

        listFor(owner).andExpect(jsonPath("$[0].title").value("Phien cu"));
    }

    @Test
    void phienCuaNguoiKhacDoiTenVaXoaDeuRa404VaKhongBiDoi() throws Exception {
        User nguoiLa = fixtures.user("nguoi-la-" + System.nanoTime() + "@x.test");

        rename(nguoiLa, conversationId, "{\"title\":\"Chiem phien\"}").andExpect(status().isNotFound());
        remove(nguoiLa, conversationId).andExpect(status().isNotFound());

        listFor(owner).andExpect(jsonPath("$[0].title").value("Phien cu"));
    }

    @Test
    void chuaDangNhapRa401() throws Exception {
        getMockMvc()
                .perform(patch("/api/v1/conversations/{id}", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        getMockMvc()
                .perform(delete("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isUnauthorized());
    }

    // ===================================== xoá phiên =====================================

    @Test
    void xoaPhienAnKhoiMoiApiCuaNguoiDungNhungGiuDuLieuThucNghiem() throws Exception {
        remove(owner, conversationId).andExpect(status().isNoContent());

        // Biến mất khỏi danh sách và mọi thao tác của người dùng.
        listFor(owner).andExpect(jsonPath("$").isEmpty());
        getMockMvc()
                .perform(get("/api/v1/conversations/{id}/messages", conversationId).with(authenticatedAs(owner)))
                .andExpect(status().isNotFound());
        getMockMvc()
                .perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hoi tiep\"}"))
                .andExpect(status().isNotFound());
        rename(owner, conversationId, "{\"title\":\"Hoi sinh\"}").andExpect(status().isNotFound());
        remove(owner, conversationId).andExpect(status().isNotFound());

        // Nhưng dữ liệu còn nguyên: đây là nguồn số liệu chương 4.
        assertThat(count("message", conversationId)).isEqualTo(1);
        assertThat(count("llm_trace", conversationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT deleted_at IS NOT NULL FROM conversation WHERE id = ?", Boolean.class, conversationId))
                .isTrue();
    }

    @Test
    void xoaMotPhienKhongDungToiPhienKhac() throws Exception {
        UUID other = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'Phien khac')",
                other,
                owner.getId(),
                run.getId());

        remove(owner, conversationId).andExpect(status().isNoContent());

        listFor(owner).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(other.toString()));
    }
}
