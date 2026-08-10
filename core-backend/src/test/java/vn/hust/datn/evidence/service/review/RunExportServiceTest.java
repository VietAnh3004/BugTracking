package vn.hust.datn.evidence.service.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.ReviewDecision;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.SubmitReviewRequest;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Xuất báo cáo.
 *
 * <p>Test then chốt là ca ghi chú chứa dấu phẩy, nháy kép và xuống dòng: nó đọc lại file bằng
 * CHÍNH thư viện CSV để chứng minh vòng ghi-đọc khép kín. Một bộ ghi tự viết sẽ sinh ra file mà
 * Excel đọc lệch cột <b>trong im lặng</b> — người chấm chỉ thấy dữ liệu sai chứ không thấy lỗi.
 */
class RunExportServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RunExportService export;

    @Autowired
    private HumanReviewService reviews;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private AnalysisRun run;
    private String shortId;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("chu-export-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 66);
        shortId = warning.getEvidenceRecord().getShortId();
    }

    private List<CSVRecord> parse(String csv) throws Exception {
        // Bỏ BOM trước khi parse — chính là việc mà Excel làm, và là lý do BOM tồn tại.
        String body = csv.startsWith("﻿") ? csv.substring(1) : csv;
        try (CSVParser parser = CSVParser.parse(
                new StringReader(body), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            return parser.getRecords();
        }
    }

    /**
     * BOM UTF-8 là BẮT BUỘC, không phải trang trí.
     *
     * <p>Excel trên Windows đoán bảng mã theo locale hệ thống chứ không mặc định UTF-8, nên file
     * không BOM mở ra là chữ rác với mọi cột tiếng Việt — và người chấm sẽ mở bằng Excel.
     */
    @Test
    void csvCoBomUtf8() {
        String csv = export.toCsv(run.getId(), owner.getId());

        assertThat(csv).startsWith("﻿");
        byte[] bytes = export.csvBytes(run.getId(), owner.getId());
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2]}).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void moiCanhBaoMotDong() throws Exception {
        fixtures.warning(run, "SQL_INJECTION", WarningTool.semgrep, "UserRepository.java", 30);

        List<CSVRecord> rows = parse(export.toCsv(run.getId(), owner.getId()));

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().toMap()).containsKeys("short_id", "file_path", "human_decision", "ground_truth_label");
    }

    /**
     * Ghi chú tiếng Việt chứa dấu phẩy, nháy kép và xuống dòng vẫn đọc lại nguyên vẹn.
     *
     * <p>Đây là lý do dùng thư viện thay vì tự nối chuỗi. Sai ở đây làm Excel lệch cột trong im
     * lặng — dữ liệu sai mà không có thông báo lỗi nào.
     */
    @Test
    void ghiChuCoDauPhayNhayKepVaXuongDong_docLaiNguyenVen() throws Exception {
        String note = "Đọc mã rồi, đúng là lỗi: findFirst trả về null, \"chắc chắn\"\nDòng thứ hai";
        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.ACCEPT, null, note, 45));

        List<CSVRecord> rows = parse(export.toCsv(run.getId(), owner.getId()));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("human_note")).isEqualTo(note);
        assertThat(rows.getFirst().get("human_decision")).isEqualTo("ACCEPT");
        assertThat(rows.getFirst().get("human_seconds_spent")).isEqualTo("45");
    }

    /** Tiếng Việt phải sống sót qua vòng String → byte → String. */
    @Test
    void tiengVietSongSotQuaByte() throws Exception {
        String note = "Cảnh báo đúng — đã kiểm chứng bằng test hỏng";
        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.ACCEPT, null, note, null));

        String decoded = new String(export.csvBytes(run.getId(), owner.getId()), StandardCharsets.UTF_8);

        assertThat(parse(decoded).getFirst().get("human_note")).isEqualTo(note);
    }

    /**
     * Nhiều người cùng xét một cảnh báo KHÔNG được nhân đôi dòng.
     *
     * <p>Ở tuần 16 chuyện này là bình thường, và một join thẳng sẽ làm file có nhiều dòng hơn số
     * cảnh báo — một lỗi trông y hệt "analyzer báo nhiều hơn thực tế".
     */
    @Test
    void haiNguoiCungXetMotCanhBao_vanChiMotDong() throws Exception {
        User nguoiKhac = fixtures.user("nguoi-khac-" + System.nanoTime() + "@x.test");
        reviews.submit(
                run.getId(), shortId, owner.getId(), new SubmitReviewRequest(ReviewDecision.ACCEPT, null, null, null));
        insertSecondReview(nguoiKhac);

        List<CSVRecord> rows = parse(export.toCsv(run.getId(), owner.getId()));

        assertThat(rows).hasSize(1);
    }

    @Test
    void chuaCoGiThiVanXuatDuocFileChiCoHeader() throws Exception {
        assertThat(parse(export.toCsv(run.getId(), owner.getId()))).hasSize(1);
    }

    /** JSON mang cả chỉ số lẫn từng dòng — và chỉ số lấy từ RunMetricsService, không tính lại. */
    @Test
    void jsonMangCaChiSoLanTungDong() {
        Map<String, Object> payload = export.toJson(run.getId(), owner.getId());

        assertThat(payload).containsKeys("analysisRunId", "metrics", "warnings");
        assertThat((List<?>) payload.get("warnings")).hasSize(1);
    }

    /**
     * Ghi thẳng review của người thứ hai.
     *
     * <p>Không đi qua service vì service chặn người không sở hữu run — nhưng ở tuần 16 người tổ
     * chức mới là chủ run, còn người tham gia thử thì không. Phần phân quyền đó là việc của tuần
     * 16; ở đây chỉ cần dựng đúng tình huống "hai người cùng xét một cảnh báo".
     */
    private void insertSecondReview(User reviewer) {
        jdbcTemplate.update(
                """
                INSERT INTO human_review (id, analysis_run_id, evidence_record_id, reviewer_id, decision)
                SELECT ?, ?, id, ?, 'REJECT' FROM evidence_record
                WHERE analysis_run_id = ? AND short_id = ?
                """,
                UUID.randomUUID(),
                run.getId(),
                reviewer.getId(),
                run.getId(),
                shortId);
    }
}
