package vn.hust.datn.evidence.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
import vn.hust.datn.evidence.dto.ReviewResponse;
import vn.hust.datn.evidence.dto.SubmitReviewRequest;
import vn.hust.datn.evidence.exception.EvidenceNotFoundException;
import vn.hust.datn.evidence.exception.InvalidReviewException;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Ghi nhận quyết định của người dùng.
 *
 * <p>Phần lớn test ở đây canh {@code messageId} — nó là <b>biến thực nghiệm</b> của user study
 * (kế hoạch §6 chia người dùng làm hai nhóm), nên nhận bừa từ client sẽ làm nhiễu đúng dữ liệu
 * mà tuần 16 sinh ra để đo.
 */
class HumanReviewServiceTest extends AbstractIntegrationTest {

    @Autowired
    private HumanReviewService service;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User reviewer;
    private AnalysisRun run;
    private String shortId;

    @BeforeEach
    void setUp() {
        reviewer = fixtures.user("nguoi-xet-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), reviewer);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 66);
        shortId = warning.getEvidenceRecord().getShortId();
    }

    /** Dựng một câu trả lời Agent thật trong DB, thuộc hội thoại của {@code owner} và của {@code targetRun}. */
    private UUID assistantMessage(User owner, AnalysisRun targetRun) {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'x')",
                conversationId,
                owner.getId(),
                targetRun.getId());
        jdbcTemplate.update(
                """
                INSERT INTO message (id, conversation_id, ordinal, role, content, verdict)
                VALUES (?, ?, 0, 'ASSISTANT', 'tom tat', 'LIKELY_TRUE_POSITIVE')
                """,
                messageId,
                conversationId);
        return messageId;
    }

    private SubmitReviewRequest request(ReviewDecision decision, UUID messageId) {
        return new SubmitReviewRequest(decision, messageId, "ghi chu", 30);
    }

    @Test
    void ghiDuocQuyetDinhKhongKemAgent() {
        ReviewResponse saved = service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, null));

        assertThat(saved.decision()).isEqualTo(ReviewDecision.ACCEPT);
        assertThat(saved.shortId()).isEqualTo(shortId);
        assertThat(saved.messageId()).as("messageId NULL = nhom doi chung cua user study").isNull();
    }

    @Test
    void ghiDuocQuyetDinhKemCauTraLoiCuaAgent() {
        UUID messageId = assistantMessage(reviewer, run);

        ReviewResponse saved =
                service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.REJECT, messageId));

        assertThat(saved.messageId()).isEqualTo(messageId);
    }

    /**
     * Gửi lại là CẬP NHẬT, không phải thêm dòng mới — và {@code createdAt} phải giữ nguyên.
     *
     * <p>{@code createdAt} là lúc người đó quyết định LẦN ĐẦU, thứ user study quan tâm. Ghi đè nó
     * khi người dùng đổi ý sẽ xoá mất mốc thời gian thật mà không ai biết.
     */
    @Test
    void guiLaiLaCapNhat_createdAtGiuNguyen() {
        ReviewResponse lanDau =
                service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, null));
        // Đọc thẳng từ DB chứ không dùng giá trị `save()` trả về: Postgres `timestamptz` chỉ có
        // độ chính xác microsecond, còn object trong bộ nhớ giữ nanosecond — so hai thứ đó sẽ đỏ
        // vì hạ tầng chứ không vì logic. Và thứ test này muốn khẳng định vốn là "giá trị TRONG DB
        // không đổi".
        OffsetDateTime truocKhiDoiY = createdAtInDb(lanDau.id());

        ReviewResponse doiY =
                service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.REJECT, null));

        assertThat(doiY.id()).isEqualTo(lanDau.id());
        assertThat(doiY.decision()).isEqualTo(ReviewDecision.REJECT);
        assertThat(createdAtInDb(doiY.id())).isEqualTo(truocKhiDoiY);
        assertThat(service.listMine(run.getId(), reviewer.getId())).hasSize(1);
    }

    private OffsetDateTime createdAtInDb(UUID reviewId) {
        return jdbcTemplate.queryForObject(
                "SELECT created_at FROM human_review WHERE id = ?", OffsetDateTime.class, reviewId);
    }

    /**
     * Trọng tâm: {@code messageId} của NGƯỜI KHÁC phải bị từ chối.
     *
     * <p>Nhận bừa thì một người chưa từng mở hội thoại vẫn đếm vào nhóm "có dùng Agent", và bảng
     * so sánh hai nhóm ở tuần 16 sai mà không ai đối chiếu lại được.
     */
    @Test
    void messageIdCuaNguoiKhac_biTuChoi() {
        User nguoiKhac = fixtures.user("nguoi-khac-" + System.nanoTime() + "@x.test");
        UUID cuaNguoiKhac = assistantMessage(nguoiKhac, run);

        assertThatThrownBy(() ->
                        service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, cuaNguoiKhac)))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("khong thuoc hoi thoai nao cua nguoi");
    }

    /** Cùng người nhưng hội thoại ở run khác: Evidence ID trong đó nói về một tập bằng chứng khác. */
    @Test
    void messageIdORunKhac_biTuChoi() {
        RepositorySnapshot snapshot = fixtures.snapshot(fixtures.project("p2-" + System.nanoTime(), reviewer));
        AnalysisRun runKhac = fixtures.succeededRun(snapshot, 1);
        UUID oRunKhac = assistantMessage(reviewer, runKhac);

        assertThatThrownBy(
                        () -> service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, oRunKhac)))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("thuoc mot lan phan tich khac");
    }

    /** Trỏ vào chính câu hỏi của mình thay vì câu trả lời của Agent là vô nghĩa. */
    @Test
    void messageIdTroVaoLuotNguoiDung_biTuChoi() {
        UUID conversationId = UUID.randomUUID();
        UUID cauHoi = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, 'x')",
                conversationId,
                reviewer.getId(),
                run.getId());
        jdbcTemplate.update(
                "INSERT INTO message (id, conversation_id, ordinal, role, content) VALUES (?, ?, 0, 'USER', 'hoi')",
                cauHoi,
                conversationId);

        assertThatThrownBy(() -> service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, cauHoi)))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("cau tra loi cua Agent");
    }

    @Test
    void messageIdKhongTonTai_biTuChoi() {
        assertThatThrownBy(() ->
                        service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, UUID.randomUUID())))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("khong ton tai");
    }

    /** Evidence ID bịa dùng lại nguyên 404 phân loại của EvidenceQueryService. */
    @Test
    void canhBaoKhongTonTai_traLoi404() {
        assertThatThrownBy(
                        () -> service.submit(run.getId(), "EV-FFFF", reviewer.getId(), request(ReviewDecision.ACCEPT, null)))
                .isInstanceOf(EvidenceNotFoundException.class);
    }

    /** Chỉ thấy quyết định của chính mình — thấy nhãn người trước sẽ hỏng tính độc lập của user study. */
    @Test
    void chiThayQuyetDinhCuaChinhMinh() {
        service.submit(run.getId(), shortId, reviewer.getId(), request(ReviewDecision.ACCEPT, null));

        List<ReviewResponse> cuaToi = service.listMine(run.getId(), reviewer.getId());

        assertThat(cuaToi).singleElement().satisfies(r -> assertThat(r.shortId()).isEqualTo(shortId));
    }
}
