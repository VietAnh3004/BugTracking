package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.testsupport.InMemoryObjectStorageClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Toàn bộ request kèm {@code .with(authenticatedAs(user))} — controller giờ đọc
 * {@code @AuthenticationPrincipal CurrentUser} thật (không phải {@code Jwt} trần của
 * {@code .with(jwt())}) để forward xuống {@code ProjectService} làm {@code ownerId}.
 */
class ProjectControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectStorageClient storageClient;

    @Test
    void taoDuAnThanhCongTraVe201VoiLocationHeader() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        String body = """
                {"name": "vulnshop-%d", "description": "case study"}
                """.formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authenticatedAs(user)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.startsWith("vulnshop-")));
    }

    @Test
    void taoDuAnThieuTenTraVe400() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authenticatedAs(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/validation-failed"));
    }

    @Test
    void layDuAnKhongTonTaiTraVe404() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(get("/api/v1/projects/" + java.util.UUID.randomUUID()).with(authenticatedAs(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));
    }

    @Test
    void danhSachTraVePageResponse() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(get("/api/v1/projects").with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void xoaDuAnTraVe204RoiKhongConTimThay() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("xoa-%d".formatted(System.nanoTime()), user);

        getMockMvc()
                .perform(delete("/api/v1/projects/" + project.getId()).with(authenticatedAs(user)))
                .andExpect(status().isNoContent());

        getMockMvc()
                .perform(get("/api/v1/projects/" + project.getId()).with(authenticatedAs(user)))
                .andExpect(status().isNotFound());
    }

    /** Không thuộc về bạn ⇒ 404, không phải 403 — và dự án của người kia phải còn nguyên. */
    @Test
    void xoaDuAnCuaNguoiKhacTraVe404VaKhongXoaGi() throws Exception {
        User owner = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        User other = fixtures.user("other-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("cua-nguoi-khac-%d".formatted(System.nanoTime()), owner);

        getMockMvc()
                .perform(delete("/api/v1/projects/" + project.getId()).with(authenticatedAs(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));

        assertThat(projectRepository.existsById(project.getId())).isTrue();
    }

    /**
     * Ca hỏng thật nếu chỉ dựa vào ON DELETE CASCADE: {@code claim_citation.evidence_record_id} là
     * NO ACTION, nên một citation trỏ vào evidence của dự án chặn cả lệnh xoá (FK violation → 500).
     * Dự án mới tạo không có hội thoại nên không lộ ra lỗi này — phải dựng đủ chuỗi tới citation.
     */
    @Test
    void xoaDuAnCoHoiThoaiTrichDanBangChungVanThanhCong() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("co-hoi-thoai-%d".formatted(System.nanoTime()), user);
        AnalysisRun run = fixtures.succeededRun(fixtures.snapshot(project), 1);
        EvidenceRecord evidence = fixtures.evidenceRecord(run, EvidenceType.CODE_CONTEXT, EvidenceSource.parser, "A.java", 1);
        UUID citationId = insertCitedConversation(user, run, evidence);

        getMockMvc()
                .perform(delete("/api/v1/projects/" + project.getId()).with(authenticatedAs(user)))
                .andExpect(status().isNoContent());

        assertThat(countById("claim_citation", citationId)).isZero();
        assertThat(countById("evidence_record", evidence.getId())).isZero();
        assertThat(countById("analysis_run", run.getId())).isZero();
    }

    @Test
    void xoaDuAnDonObjectStorageCuaDungSnapshotCuaNo() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("co-file-%d".formatted(System.nanoTime()), user);
        Project keep = fixtures.project("giu-lai-%d".formatted(System.nanoTime()), user);
        String deletedKey = storedFileOf(fixtures.snapshot(project));
        String keptKey = storedFileOf(fixtures.snapshot(keep));

        getMockMvc()
                .perform(delete("/api/v1/projects/" + project.getId()).with(authenticatedAs(user)))
                .andExpect(status().isNoContent());

        InMemoryObjectStorageClient storage = (InMemoryObjectStorageClient) storageClient;
        assertThat(storage.containsKey(deletedKey)).isFalse();
        assertThat(storage.containsKey(keptKey)).isTrue();
    }

    /** Snapshot của fixture mang storage key giả ({@code test/...}); đổi sang dạng thật rồi ghi một object. */
    private String storedFileOf(RepositorySnapshot snapshot) {
        String prefix = "snapshots/" + snapshot.getId() + "/";
        jdbcTemplate.update("UPDATE repository_snapshot SET storage_key = ? WHERE id = ?", prefix, snapshot.getId());
        String key = prefix + "src/A.java";
        storageClient.putObject(key, "class A {}".getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
        return key;
    }

    private UUID insertCitedConversation(User owner, AnalysisRun run, EvidenceRecord evidence) {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        UUID citationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, ?)",
                conversationId, owner.getId(), run.getId(), "Hoi ve mot canh bao");
        jdbcTemplate.update(
                "INSERT INTO message (id, conversation_id, ordinal, role, content, verdict) VALUES (?, ?, 1, 'ASSISTANT', ?, ?)",
                messageId, conversationId, "tra loi", "INSUFFICIENT_EVIDENCE");
        jdbcTemplate.update(
                "INSERT INTO claim (id, message_id, ordinal, text, verdict) VALUES (?, ?, 1, ?, ?)",
                claimId, messageId, "nhan dinh", "INSUFFICIENT_EVIDENCE");
        jdbcTemplate.update(
                "INSERT INTO claim_citation (id, claim_id, cited_short_id_raw, evidence_record_id, resolution) "
                        + "VALUES (?, ?, ?, ?, 'RESOLVED')",
                citationId, claimId, "EV-0001A", evidence.getId());
        return citationId;
    }

    private int countById(String table, UUID id) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    @Test
    void khongCoTokenTraVe401() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/unauthenticated"));
    }
}
