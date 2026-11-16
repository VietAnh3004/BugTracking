package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Gán nhãn ground truth: nguồn nhãn theo vai trò, tính "mù" do server bảo đảm, và nạp/xuất file.
 *
 * <p>Ca quan trọng nhất là {@link #thanhVienKhongBaoGioThayNhanCuaChuDuAnHayGoiYSeeded}: nếu người
 * gán thứ hai nhìn được nhãn của người gán chính thì Cohen's kappa không còn đo hai lần đọc mã độc
 * lập, mà đo một người chép lại người kia — và con số đó đi thẳng vào chương 4.
 */
class GroundTruthControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User member;
    private AnalysisRun run;
    private String md5ShortId;
    private String semgrepMd5ShortId;

    @BeforeEach
    void setUp() throws Exception {
        owner = fixtures.user("gt-chu-" + System.nanoTime() + "@x.test");
        member = fixtures.user("gt-tv-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("gt-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 3);
        md5ShortId = fixtures.warning(run, "WEAK_MESSAGE_DIGEST_MD5", WarningTool.spotbugs, "src/PasswordHasher.java", 53)
                .getEvidenceRecord()
                .getShortId();
        // Cùng vị trí, công cụ khác: một mục YAML bỏ trống tool/rule phải khớp CẢ HAI.
        semgrepMd5ShortId = fixtures.warning(run, "use-of-md5", WarningTool.semgrep, "src/PasswordHasher.java", 53)
                .getEvidenceRecord()
                .getShortId();
        fixtures.warning(run, "PREDICTABLE_RANDOM", WarningTool.spotbugs, "src/ColorPicker.java", 21);

        getMockMvc()
                .perform(post("/api/v1/projects/{id}/members", project.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(member.getEmail())))
                .andExpect(status().isOk());
    }

    private void label(User who, String shortId, String label, String rationale) throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/ground-truth", run.getId(), shortId)
                        .with(authenticatedAs(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"%s\",\"rationale\":\"%s\"}".formatted(label, rationale)))
                .andExpect(status().isOk());
    }

    private String sourceOfLabelOn(String shortId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT string_agg(g.source, ',' ORDER BY g.source) FROM ground_truth_label g
                JOIN evidence_record e ON e.id = g.evidence_record_id
                WHERE e.analysis_run_id = ? AND e.short_id = ?
                """,
                String.class,
                run.getId(),
                shortId);
    }

    @Test
    void chuDuAnGhiManual_thanhVienGhiBlind() throws Exception {
        label(owner, md5ShortId, "TRUE_POSITIVE", "MD5 bam mat khau");
        label(member, md5ShortId, "FALSE_POSITIVE", "Toi doc ma thay khac");

        assertThat(sourceOfLabelOn(md5ShortId)).isEqualTo("BLIND_SECOND_RATER,MANUAL");
    }

    @Test
    void thanhVienKhongBaoGioThayNhanCuaChuDuAnHayGoiYSeeded() throws Exception {
        label(owner, md5ShortId, "TRUE_POSITIVE", "MD5 bam mat khau");
        importYaml(owner, seededYaml()).andExpect(status().isOk());

        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/ground-truth", run.getId()).with(authenticatedAs(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.source").value("BLIND_SECOND_RATER"))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[*].label").doesNotExist())
                .andExpect(jsonPath("$.rows[*].seededHint").doesNotExist());

        // Chủ dự án thì thấy nhãn của mình VÀ gợi ý SEEDED.
        getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/ground-truth", run.getId()).with(authenticatedAs(owner)))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.rows[?(@.shortId == '%s')].label.label".formatted(md5ShortId))
                        .value("TRUE_POSITIVE"))
                .andExpect(jsonPath("$.rows[?(@.shortId == '%s')].seededHint.label".formatted(md5ShortId))
                        .value("TRUE_POSITIVE"));
    }

    @Test
    void thieuLyDo_400() throws Exception {
        getMockMvc()
                .perform(put("/api/v1/analysis-runs/{runId}/evidence/{shortId}/ground-truth", run.getId(), md5ShortId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"TRUE_POSITIVE\",\"rationale\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    private String seededYaml() {
        return """
                version: 1
                labels:
                  - file: src/PasswordHasher.java
                    line: 53
                    source: SEEDED
                    label: TRUE_POSITIVE
                    rationale: TP-05 - MD5 bam mat khau
                  - file: src/KhongTonTai.java
                    line: 1
                    source: SEEDED
                    label: FALSE_POSITIVE
                    rationale: khong khop canh bao nao
                """;
    }

    private org.springframework.test.web.servlet.ResultActions importYaml(User who, String yaml) throws Exception {
        return getMockMvc()
                .perform(multipart("/api/v1/analysis-runs/{runId}/ground-truth/import", run.getId())
                        .file(new MockMultipartFile(
                                "file", "labels.yaml", "application/yaml", yaml.getBytes(StandardCharsets.UTF_8)))
                        .with(authenticatedAs(who)));
    }

    /** Mục bỏ trống tool/rule khớp MỌI cảnh báo ở vị trí đó; mục không khớp được LIỆT KÊ ra. */
    @Test
    void napFile_khopTheoViTri_vaLietKeMucKhongKhop() throws Exception {
        importYaml(owner, seededYaml())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labelsWritten").value(2))
                .andExpect(jsonPath("$.entriesMatched").value(1))
                .andExpect(jsonPath("$.unmatched[0]").value("*/*@src/KhongTonTai.java:1"));

        assertThat(sourceOfLabelOn(md5ShortId)).isEqualTo("SEEDED");
        assertThat(sourceOfLabelOn(semgrepMd5ShortId)).isEqualTo("SEEDED");
    }

    /** File có mục sai ⇒ 400 và KHÔNG ghi mục nào, kể cả những mục đúng đứng trước. */
    @Test
    void fileCoMucSai_khongGhiGiCa() throws Exception {
        String yaml =
                """
                labels:
                  - file: src/PasswordHasher.java
                    line: 53
                    source: MANUAL
                    label: TRUE_POSITIVE
                    rationale: dung
                  - file: src/ColorPicker.java
                    line: 21
                    source: MANUAL
                    label: KHONG_CO_NHAN_NAY
                    rationale: sai nhan
                """;

        importYaml(owner, yaml)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("muc #2")));

        assertThat(sourceOfLabelOn(md5ShortId)).isNull();
    }

    /** Xuất rồi nạp lại vào CHÍNH run đó: nhãn y nguyên — vòng tròn mà phân tích lại cần. */
    @Test
    void xuatRoiNapLai_giuNguyenNhan() throws Exception {
        label(owner, md5ShortId, "TRUE_POSITIVE", "MD5 bam mat khau");
        label(member, md5ShortId, "UNCERTAIN", "Chua chac");

        String exported = getMockMvc()
                .perform(get("/api/v1/analysis-runs/{runId}/ground-truth/export.yaml", run.getId())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(exported).contains("source: MANUAL").contains("source: BLIND_SECOND_RATER");

        jdbcTemplate.update(
                "DELETE FROM ground_truth_label WHERE evidence_record_id IN (SELECT id FROM evidence_record WHERE analysis_run_id = ?)",
                run.getId());
        importYaml(owner, exported).andExpect(status().isOk()).andExpect(jsonPath("$.labelsWritten").value(2));

        assertThat(sourceOfLabelOn(md5ShortId)).isEqualTo("BLIND_SECOND_RATER,MANUAL");
    }

    @Test
    void thanhVienKhongNapFileDuoc() throws Exception {
        importYaml(member, seededYaml()).andExpect(status().isNotFound());
    }
}
