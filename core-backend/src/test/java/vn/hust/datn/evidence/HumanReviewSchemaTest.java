package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Bốn bất biến mà V9 ép ở tầng DB.
 *
 * <p>Cả bốn canh cùng một loại hỏng: dữ liệu ghi xuống trông bình thường trong khi chỉ số của
 * chương 4 đọc từ nó đã sai. Ép ở DB chứ không chỉ trong Java vì `human_review` là mẫu số của
 * Human acceptance rate và là nơi chứa biến thực nghiệm của user study.
 */
class HumanReviewSchemaTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestFixtures fixtures;

    private User reviewer;
    private AnalysisRun run;
    private EvidenceRecord canhBao;

    @BeforeEach
    void setUp() {
        reviewer = fixtures.user("nguoi-xet-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), reviewer);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        canhBao = fixtures.evidenceRecord(
                run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "UserService.java", 66);
    }

    private void insertReview(UUID evidenceId, UUID reviewerId, String decision, Integer seconds) {
        jdbcTemplate.update(
                """
                INSERT INTO human_review
                    (id, analysis_run_id, evidence_record_id, reviewer_id, decision, seconds_spent)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                run.getId(),
                evidenceId,
                reviewerId,
                decision,
                seconds);
    }

    @Test
    void ghiDuocMotQuyetDinh() {
        insertReview(canhBao.getId(), reviewer.getId(), "ACCEPT", 42);

        String decision = jdbcTemplate.queryForObject(
                "SELECT decision FROM human_review WHERE evidence_record_id = ?", String.class, canhBao.getId());
        assertThat(decision).isEqualTo("ACCEPT");
    }

    /**
     * Một người MỘT quyết định cho một cảnh báo.
     *
     * <p>Không có ràng buộc này thì Human acceptance rate đếm trùng người đổi ý nhiều lần — và
     * đếm trùng theo hướng nào là ngẫu nhiên, nên con số trong báo cáo sai mà không ai đối chiếu
     * lại được.
     */
    @Test
    void motNguoiKhongXetMotCanhBaoHaiLan() {
        insertReview(canhBao.getId(), reviewer.getId(), "ACCEPT", null);

        assertThatThrownBy(() -> insertReview(canhBao.getId(), reviewer.getId(), "REJECT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Hai người khác nhau vẫn xét được cùng một cảnh báo — đó là cả điểm của user study. */
    @Test
    void haiNguoiXetCungMotCanhBaoThiDuoc() {
        User nguoiKhac = fixtures.user("nguoi-khac-" + System.nanoTime() + "@x.test");

        insertReview(canhBao.getId(), reviewer.getId(), "ACCEPT", null);
        insertReview(canhBao.getId(), nguoiKhac.getId(), "REJECT", null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM human_review WHERE evidence_record_id = ?", Integer.class, canhBao.getId());
        assertThat(count).isEqualTo(2);
    }

    /** 0 giây là dữ liệu rác, không phải "rất nhanh". */
    @Test
    void thoiGianBangKhongBiTuChoi() {
        assertThatThrownBy(() -> insertReview(canhBao.getId(), reviewer.getId(), "ACCEPT", 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_human_review_seconds_positive");
    }

    /** Không đo được thời gian là chuyện bình thường; ép NOT NULL sẽ khiến client bịa số. */
    @Test
    void thoiGianNullThiChapNhan() {
        insertReview(canhBao.getId(), reviewer.getId(), "UNCERTAIN", null);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT seconds_spent FROM human_review WHERE evidence_record_id = ?",
                        Integer.class,
                        canhBao.getId()))
                .isNull();
    }

    /**
     * Xoá một hội thoại KHÔNG được xoá luôn quyết định của người dùng.
     *
     * <p>Mất quyết định là mất dữ liệu thực nghiệm không tái tạo được; mất liên kết tới hội thoại
     * thì chỉ mất ngữ cảnh. Vì vậy {@code ON DELETE SET NULL}, không phải {@code CASCADE}.
     */
    @Test
    void xoaHoiThoaiKhongXoaQuyetDinh() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'x')",
                conversationId,
                reviewer.getId(),
                run.getId());
        jdbcTemplate.update(
                """
                INSERT INTO message (id, conversation_id, ordinal, role, content, verdict)
                VALUES (?, ?, 0, 'ASSISTANT', 'tom tat', 'LIKELY_TRUE_POSITIVE')
                """,
                messageId,
                conversationId);
        jdbcTemplate.update(
                """
                INSERT INTO human_review
                    (id, analysis_run_id, evidence_record_id, reviewer_id, decision, message_id)
                VALUES (?, ?, ?, ?, 'ACCEPT', ?)
                """,
                UUID.randomUUID(),
                run.getId(),
                canhBao.getId(),
                reviewer.getId(),
                messageId);

        jdbcTemplate.update("DELETE FROM conversation WHERE id = ?", conversationId);

        Integer conLai = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM human_review WHERE evidence_record_id = ?", Integer.class, canhBao.getId());
        assertThat(conLai).as("Quyet dinh cua nguoi dung phai con nguyen").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT message_id FROM human_review WHERE evidence_record_id = ?",
                        UUID.class,
                        canhBao.getId()))
                .isNull();
    }

    /**
     * Nhãn chuẩn phải kèm lý do. Kế hoạch §5 cảnh báo cái bẫy circularity — gán nhãn theo output
     * của Agent — và bắt viết lý do là cách rẻ nhất để nó lộ ra.
     */
    @Test
    void nhanChuanThieuLyDoBiTuChoi() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO ground_truth_label
                            (evidence_record_id, source, label, rationale, labeler)
                        VALUES (?, 'MANUAL', 'TRUE_POSITIVE', '   ', 'toi')
                        """,
                        canhBao.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ground_truth_rationale_not_blank");
    }

    /** Cùng một cảnh báo có nhãn từ hai nguồn — điều kiện để tuần 15 tính được Cohen's kappa. */
    @Test
    void cungCanhBaoCoNhanTuHaiNguon() {
        jdbcTemplate.update(
                """
                INSERT INTO ground_truth_label (evidence_record_id, source, label, rationale, labeler)
                VALUES (?, 'MANUAL', 'TRUE_POSITIVE', 'Doc ma: findFirst tra ve null', 'nguoi-chinh')
                """,
                canhBao.getId());
        jdbcTemplate.update(
                """
                INSERT INTO ground_truth_label (evidence_record_id, source, label, rationale, labeler)
                VALUES (?, 'BLIND_SECOND_RATER', 'UNCERTAIN', 'Khong chac nhanh nay toi duoc', 'nguoi-mu')
                """,
                canhBao.getId());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ground_truth_label WHERE evidence_record_id = ?", Integer.class, canhBao.getId());
        assertThat(count).isEqualTo(2);
    }
}
