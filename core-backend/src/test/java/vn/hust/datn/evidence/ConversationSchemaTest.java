package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Ba bất biến mà V8 ép ở tầng DB — tất cả đều canh cùng một loại hỏng: một lượt trả lời của agent
 * ghi xuống trông bình thường trong khi số liệu tuần 15 đọc từ nó đã sai.
 *
 * <p>Ép ở DB chứ không chỉ trong Java vì chúng là tiền đề của hai chỉ số trong báo cáo
 * (Unsupported Claim Rate, số lần hạ cấp verdict). Một bug ở tầng service chỉ làm sai vài dòng;
 * một bug làm sai định nghĩa của mẫu số thì làm sai cả chương 4.
 */
class ConversationSchemaTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestFixtures fixtures;

    private UUID aConversation() {
        User owner = fixtures.user("chu-so-huu-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversation (id, owner_id, analysis_run_id, title) VALUES (?, ?, ?, ?)",
                id,
                owner.getId(),
                run.getId(),
                "Hoi ve mot canh bao");
        return id;
    }

    private void insertMessage(UUID conversationId, int ordinal, String role, String verdict, String downgradedFrom) {
        jdbcTemplate.update(
                """
                INSERT INTO message (id, conversation_id, ordinal, role, content, verdict, downgraded_from_verdict)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                conversationId,
                ordinal,
                role,
                "noi dung",
                verdict,
                downgradedFrom);
    }

    /** Đường đi bình thường: hỏi rồi đáp, mỗi lượt một ordinal. */
    @Test
    void luotHoiVaLuotDap_ghiDuocBinhThuong() {
        UUID conversationId = aConversation();

        insertMessage(conversationId, 0, "USER", null, null);
        insertMessage(conversationId, 1, "ASSISTANT", "LIKELY_TRUE_POSITIVE", null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM message WHERE conversation_id = ?", Integer.class, conversationId);
        assertThat(count).isEqualTo(2);
    }

    /**
     * Một lượt của agent KHÔNG có verdict nghĩa là nó hỏng giữa chừng mà vẫn được ghi thành câu
     * trả lời — hệt như một run "thành công" với Evidence Store rỗng.
     */
    @Test
    void luotAgentThieuVerdict_biTuChoi() {
        UUID conversationId = aConversation();

        assertThatThrownBy(() -> insertMessage(conversationId, 0, "ASSISTANT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_message_verdict_matches_role");
    }

    /** Chiều ngược lại cũng phải chặn: câu hỏi của người dùng không thể mang kết luận. */
    @Test
    void luotNguoiDungCoVerdict_biTuChoi() {
        UUID conversationId = aConversation();

        assertThatThrownBy(() -> insertMessage(conversationId, 0, "USER", "LIKELY_FALSE_POSITIVE", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_message_verdict_matches_role");
    }

    /**
     * Hạ cấp chỉ đi MỘT chiều: xuống {@code INSUFFICIENT_EVIDENCE}. Ghi {@code downgraded_from} mà
     * verdict hiện tại là thứ khác nghĩa là hậu kiểm hỏng — và cái hỏng đó đi thẳng vào báo cáo
     * dưới dạng "số lần hạ cấp" mà không ai đối chiếu lại được.
     */
    @Test
    void haCapSangVerdictKhongPhaiInsufficient_biTuChoi() {
        UUID conversationId = aConversation();

        assertThatThrownBy(() ->
                        insertMessage(conversationId, 0, "ASSISTANT", "LIKELY_TRUE_POSITIVE", "LIKELY_FALSE_POSITIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_message_downgrade_is_one_way");
    }

    @Test
    void haCapDungChieu_ghiDuoc() {
        UUID conversationId = aConversation();

        insertMessage(conversationId, 0, "ASSISTANT", "INSUFFICIENT_EVIDENCE", "LIKELY_TRUE_POSITIVE");

        String from = jdbcTemplate.queryForObject(
                "SELECT downgraded_from_verdict FROM message WHERE conversation_id = ?", String.class, conversationId);
        assertThat(from).isEqualTo("LIKELY_TRUE_POSITIVE");
    }

    /**
     * V2 để {@code claim.message_id} không có FK kèm lời hứa "thêm ràng buộc ở tuần 12-13". Test
     * này chứng minh nợ đó đã trả: một claim mồ côi không ghi được nữa, nên không có claim nào
     * lọt vào mẫu số Unsupported Claim Rate mà không truy được về lượt trả lời sinh ra nó.
     */
    @Test
    void claimMoCoiKhongConGhiDuoc() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO claim (id, message_id, ordinal, text, verdict)
                        VALUES (?, ?, 0, 'mot nhan dinh', 'LIKELY_TRUE_POSITIVE')
                        """,
                        UUID.randomUUID(),
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_claim_message");
    }

    /**
     * {@code llm_trace} phải ghi được cả lượt HỎNG ({@code message_id} NULL) — đó chính là dữ liệu
     * cho biết agent chết ở vòng thứ mấy, thứ không tái tạo được sau.
     */
    @Test
    void llmTraceGhiDuocLuotHongChuaCoMessage() {
        UUID conversationId = aConversation();

        jdbcTemplate.update(
                """
                INSERT INTO llm_trace (id, conversation_id, message_id, iteration, provider, model,
                                       prompt_version, error_message)
                VALUES (?, ?, NULL, 3, 'anthropic', 'claude-sonnet-5', 'v1', 'het so vong cho phep')
                """,
                UUID.randomUUID(),
                conversationId);

        Integer iteration = jdbcTemplate.queryForObject(
                "SELECT iteration FROM llm_trace WHERE conversation_id = ? AND message_id IS NULL",
                Integer.class,
                conversationId);
        assertThat(iteration).isEqualTo(3);
    }
}
