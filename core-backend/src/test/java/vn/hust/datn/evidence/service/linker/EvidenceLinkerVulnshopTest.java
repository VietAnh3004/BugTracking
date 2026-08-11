package vn.hust.datn.evidence.service.linker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.service.analysis.AnalysisRunService;
import vn.hust.datn.evidence.service.analysis.NamedArtifact;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;
import vn.hust.datn.evidence.testsupport.TestFixtures;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

/**
 * EvidenceLinker chạy trên TOÀN BỘ dữ liệu thật của vulnshop.
 *
 * <h2>Vì sao cần, khi đã có {@code EvidenceLinkerTest} dựng tay</h2>
 *
 * Test dựng tay chứng minh từng nhánh thuật toán chạy đúng trên dữ liệu nó tự tạo ra. Nó KHÔNG
 * chứng minh được thứ quan trọng hơn: rằng bốn parser, chuẩn hoá đường dẫn, JavaParser và ba tầng
 * liên kết ghép lại với nhau vẫn ra kết quả có nghĩa trên một dự án Java thật.
 *
 * <p>Đây cũng là chỗ KHOÁ LẠI những con số mà tuần 15 sẽ báo cáo. Không có nó, một lần chỉnh
 * heuristic làm số liên kết tụt từ 123 xuống 40 sẽ không có gì đỏ.
 *
 * <h2>Assert theo TÍNH CHẤT, không theo số tuyệt đối</h2>
 *
 * Heuristic của linker còn phải sửa, và một test khoá cứng "đúng 123 liên kết" sẽ đỏ mỗi lần
 * chỉnh, khiến người ta sửa test cho xanh thay vì đọc xem số đổi có đúng không. Nên ở đây khoá
 * những thứ KHÔNG được phép đổi: mọi cảnh báo phải có liên kết, cả bốn quan hệ phải có mặt, ca
 * TP-16 phải sinh `STACK_FRAME`, và tỉ lệ liên kết yếu phải nằm dưới ngưỡng.
 */
class EvidenceLinkerVulnshopTest extends AbstractIntegrationTest {

    @Autowired
    private AnalysisRunService analysisRunService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EvidenceLinker linker;

    private AnalysisRun run;

    @BeforeEach
    void napBoArtifactThat() throws Exception {
        User owner = fixtures.user("linker-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("linker-%d".formatted(System.nanoTime()), owner);
        RepositorySnapshot snapshot =
                fixtures.readySnapshotWithContent(project, SampleArtifacts.vulnshopSourceContents());

        ZipBuilder zip = new ZipBuilder();
        for (Path report : SampleArtifacts.surefireReports()) {
            zip.file("target/surefire-reports/" + report.getFileName(), SampleArtifacts.bytesOf(report));
        }
        List<NamedArtifact> artifacts = new ArrayList<>(List.of(
                new NamedArtifact("spotbugs.xml", SampleArtifacts.spotbugsXml()),
                new NamedArtifact("semgrep.sarif", SampleArtifacts.semgrepSarif()),
                new NamedArtifact("surefire.zip", zip.build()),
                new NamedArtifact("jacoco.xml", SampleArtifacts.jacocoXml())));

        run = analysisRunService.ingest(snapshot.getId(), owner.getId(), artifacts);
    }

    private List<Map<String, Object>> links(String where, Object... args) {
        return jdbcTemplate.queryForList(
                """
                SELECT l.relation, l.method, l.weight, l.rationale,
                       f.short_id AS from_short, f.type AS from_type, f.file_path AS from_path,
                       t.short_id AS to_short,   t.type AS to_type,   t.file_path AS to_path,
                       t.start_line AS to_line
                  FROM evidence_link l
                  JOIN evidence_record f ON f.id = l.from_evidence_id
                  JOIN evidence_record t ON t.id = l.to_evidence_id
                 WHERE l.analysis_run_id = ? %s
                """
                        .formatted(where),
                concat(run.getId(), args));
    }

    private static Object[] concat(Object first, Object... rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    /** Bất biến số một: không cảnh báo nào bị bỏ lại. */
    @Test
    void moiCanhBaoDeuCoItNhatMotLienKet() {
        assertThat(run.getLinkCount()).isPositive();
        assertThat(run.getUnlinkedWarningCount()).isZero();
        assertThat(run.getCodeContextCount()).isPositive();
    }

    /**
     * Cả bốn quan hệ phải có mặt. Thiếu một cái nghĩa là một tầng chạy nhưng không sinh gì — kiểu
     * hỏng mà tổng số liên kết che được.
     */
    @Test
    void caBonQuanHeDeuCoMat() {
        assertThat(links("").stream().map(link -> link.get("relation")).distinct())
                .containsExactlyInAnyOrder(
                        EvidenceLinkRelation.LOCATED_IN.name(),
                        EvidenceLinkRelation.COVERS.name(),
                        EvidenceLinkRelation.SUPPORTS.name(),
                        EvidenceLinkRelation.CONTRADICTS.name());
    }

    /**
     * TP-16 là ca DUY NHẤT của vulnshop mà một cảnh báo tĩnh và một stack frame của test hỏng cùng
     * trỏ về đúng một dòng ({@code UserService.java:66}). Nó tồn tại chính vì trước đó tầng 3 chạy
     * đúng mà sinh 0 liên kết — xem SEEDED.md.
     *
     * <p>Mất ca này là mất mẫu DUY NHẤT của loại bằng chứng thuyết phục nhất cho verdict
     * {@code LIKELY_TRUE_POSITIVE}, và tuần 12-13 không có gì để demo.
     */
    @Test
    void tp16_sinhDungMotLienKetStackFrameTuTestHongToiCanhBao() {
        List<Map<String, Object>> stackFrameLinks = links("AND l.method = 'STACK_FRAME'");

        assertThat(stackFrameLinks).hasSize(1);
        Map<String, Object> link = stackFrameLinks.getFirst();
        assertThat(link.get("relation")).isEqualTo(EvidenceLinkRelation.SUPPORTS.name());
        assertThat(link.get("from_type")).isEqualTo("FAILED_TEST");
        assertThat(link.get("to_type")).isEqualTo("STATIC_WARNING");
        assertThat((String) link.get("to_path")).endsWith("UserService.java");
        assertThat(link.get("to_line")).isEqualTo(66);
    }

    /**
     * CẠM BẪY TRUNG TÂM của vulnshop: {@code findByUsername} có SQL injection THẬT, test đường đi
     * bình thường vẫn xanh, và có một test xanh khác còn CHỨNG MINH lỗ hổng có thật.
     *
     * <p>Hệ thống phải làm đúng hai việc ở đây, và test này canh cả hai:
     *
     * <ol>
     *   <li>Vẫn sinh {@code CONTRADICTS} — đó là dữ liệu đúng, "có test xanh phủ dòng này" là một
     *       sự thật quan sát được.
     *   <li>KHÔNG hạ cảnh báo đi, và kèm sẵn câu nói rõ CONTRADICTS không có nghĩa là false
     *       positive. Câu đó nằm trong chính dữ liệu chứ không chỉ trong prompt, để nó đi theo bằng
     *       chứng tới mọi nơi bằng chứng đó tới.
     * </ol>
     */
    @Test
    void caBayFindByUsername_sinhContradictsNhungKhongHaCanhBao() {
        List<Map<String, Object>> contradicts = links(
                "AND l.relation = 'CONTRADICTS' AND t.file_path LIKE '%UserRepository.java'");

        assertThat(contradicts).isNotEmpty();
        assertThat(contradicts).allSatisfy(link -> {
            assertThat(link.get("from_type")).isEqualTo("TEST_RESULT");
            assertThat((String) link.get("rationale")).contains("KHÔNG chứng minh cảnh báo sai");
        });

        // Cảnh báo SQL injection vẫn còn nguyên trong Evidence Store, không bị hạ hay xoá.
        Integer conCanhBao = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM warning w JOIN evidence_record e ON e.id = w.evidence_record_id
                 WHERE e.analysis_run_id = ? AND e.file_path LIKE '%UserRepository.java'
                """,
                Integer.class,
                run.getId());
        assertThat(conCanhBao).isPositive();

        // Và weight của CONTRADICTS luôn thấp hơn mọi SUPPORTS — LLM phải thấy được thứ bậc đó.
        double maxContradicts = contradicts.stream()
                .mapToDouble(link -> ((Number) link.get("weight")).doubleValue())
                .max()
                .orElseThrow();
        double minSupports = links("AND l.relation = 'SUPPORTS'").stream()
                .mapToDouble(link -> ((Number) link.get("weight")).doubleValue())
                .min()
                .orElseThrow();
        assertThat(maxContradicts).isLessThanOrEqualTo(minSupports);
    }

    /**
     * Tỉ lệ liên kết chỉ dựa vào quy ước tên — con số tuần 15 phải báo cáo.
     *
     * <p>Khoá bằng NGƯỠNG chứ không bằng số chính xác: heuristic còn sửa, nhưng nếu tỉ lệ này vọt
     * lên quá nửa thì phần lớn "bằng chứng" của hệ thống chỉ là suy đoán theo tên, và kết luận của
     * chương 4 mất căn cứ.
     */
    @Test
    void tiLeLienKetYeuNamDuoiNguong() {
        List<Map<String, Object>> all = links("");
        long weak = all.stream()
                .filter(link -> EvidenceLinkMethod.NAMING_CONVENTION.name().equals(link.get("method")))
                .count();

        assertThat(all).isNotEmpty();
        assertThat((double) weak / all.size()).isLessThan(0.5);
        // Và bộ đếm trên analysis_run phải nói ra cùng một câu chuyện.
        assertThat(run.getLinkCountsByMethod()).isNotEmpty();
    }

    /** Bất biến của cột phi chuẩn hoá {@code evidence_link.analysis_run_id}. */
    @Test
    void khongLienKetNaoVatQuaHaiRun() {
        Integer viPham = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM evidence_link l
                  JOIN evidence_record f ON f.id = l.from_evidence_id
                  JOIN evidence_record t ON t.id = l.to_evidence_id
                 WHERE l.analysis_run_id = ?
                   AND (f.analysis_run_id <> l.analysis_run_id OR t.analysis_run_id <> l.analysis_run_id)
                """,
                Integer.class,
                run.getId());

        assertThat(viPham).isZero();
    }

    /**
     * Ngữ cảnh mã nguồn phải trỏ đúng method bao quanh — ví dụ chạy suốt docs/evidence-model.md.
     *
     * <p>Lọc theo RULE chứ không theo file của cảnh báo, và đó là điểm tinh tế: một cảnh báo có
     * thể có nhiều vị trí ở NHIỀU FILE KHÁC NHAU ({@code UNSAFE_HASH_EQUALS} của vulnshop trỏ tới
     * ba file — phát hiện 1). Tầng 1 nối mỗi vị trí vào ngữ cảnh trong file của chính nó, nên
     * "mọi context của cảnh báo ở PasswordHasher đều thuộc PasswordHasher" là một kỳ vọng SAI.
     */
    @Test
    void md5PasswordHasher_noiToiDungMethodBaoQuanh() {
        List<String> symbols = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT t.symbol FROM evidence_link l
                  JOIN evidence_record f ON f.id = l.from_evidence_id
                  JOIN warning w        ON w.evidence_record_id = f.id
                  JOIN evidence_record t ON t.id = l.to_evidence_id
                 WHERE l.analysis_run_id = ? AND l.relation = 'LOCATED_IN'
                   AND w.rule_id = 'WEAK_MESSAGE_DIGEST_MD5' AND t.symbol IS NOT NULL
                """,
                String.class,
                run.getId());

        assertThat(symbols).isNotEmpty();
        assertThat(symbols).allSatisfy(symbol -> assertThat(symbol)
                .startsWith("vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#")
                .isIn(
                        "vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#hashPassword",
                        "vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#checksumForDeduplication",
                        "vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#md5Hex"));
    }

    /**
     * Cảnh báo nhiều vị trí phải nối tới ngữ cảnh ở CẢ BA file, không chỉ file của vị trí chính.
     *
     * <p>{@code UNSAFE_HASH_EQUALS} trỏ tới {@code PasswordHasher.java}, {@code AuthService.java}
     * và {@code User.java} — chính là lý do {@code warning_location} phải là bảng 1:N. Nếu tầng 1
     * chỉ đi theo {@code evidence_record.start_line} (vị trí chính) thì 2/3 bằng chứng biến mất mà
     * tổng số liên kết vẫn trông bình thường.
     */
    @Test
    void canhBaoNhieuViTri_noiToiNguCanhOCaBaFile() {
        List<String> paths = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT t.file_path FROM evidence_link l
                  JOIN evidence_record f ON f.id = l.from_evidence_id
                  JOIN warning w        ON w.evidence_record_id = f.id
                  JOIN evidence_record t ON t.id = l.to_evidence_id
                 WHERE l.analysis_run_id = ? AND l.relation = 'LOCATED_IN'
                   AND w.rule_id = 'UNSAFE_HASH_EQUALS'
                """,
                String.class,
                run.getId());

        assertThat(paths).hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * Chạy lại linker trên dữ liệu THẬT phải cho kết quả giống hệt, và không vỡ
     * {@code UNIQUE (from, to, relation)}.
     *
     * <p>Khác ca cùng tên trong {@code EvidenceLinkerTest}: ở đó dữ liệu dựng tay chỉ có vài liên
     * kết, còn ở đây là hơn một trăm liên kết sinh từ cả ba tầng — đủ để một lỗi dedup lộ ra.
     */
    @Test
    void chayLaiTrenDuLieuThat_ketQuaGiongHetVaKhongVoRangBuoc() {
        UUID runId = run.getId();
        int truoc = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence_link WHERE analysis_run_id = ?", Integer.class, runId);
        assertThat(truoc).isPositive();

        LinkResult lanHai = linker.relink(runId);

        assertThat(lanHai.linkCount()).isEqualTo(truoc);
        assertThat(lanHai.deletedCount()).isEqualTo(truoc);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_link WHERE analysis_run_id = ?", Integer.class, runId))
                .isEqualTo(truoc);
    }
}
