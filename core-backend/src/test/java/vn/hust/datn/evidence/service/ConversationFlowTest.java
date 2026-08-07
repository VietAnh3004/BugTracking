package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ClaimVerdict;
import vn.hust.datn.evidence.domain.MessageRole;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.ChatMessageResponse;
import vn.hust.datn.evidence.dto.ConversationResponse;
import vn.hust.datn.evidence.dto.CreateConversationRequest;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.service.agent.AgentClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Toàn bộ một lượt hỏi đáp, từ lúc mở phiên tới lúc câu trả lời nằm trong DB.
 *
 * <p>agent-service được thay bằng spy: quy ước của repo là KHÔNG gọi LLM thật trong test, và ở đây
 * còn thêm một lý do nữa — thứ đang kiểm là <b>hậu kiểm trích dẫn và việc ghi dữ liệu</b>, nên câu
 * trả lời của mô hình phải là hằng số thì mới khẳng định được điều gì.
 *
 * <p>Spy chứ không phải mock: chỉ {@code analyze} bị thay, còn {@code parse} chạy thật — nếu mock
 * cả hai thì test sẽ xanh ngay cả khi payload giữa hai service lệch nhau, mà đó chính là loại lỗi
 * duy nhất mà một ranh giới HTTP sinh ra.
 */
class ConversationFlowTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AgentClient agentClient;

    private User owner;
    private AnalysisRun run;
    private String warningShortId;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("chu-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "NP_NULL_ON_SOME_PATH", WarningTool.spotbugs, "UserService.java", 66);
        warningShortId = warning.getEvidenceRecord().getShortId();
    }

    /** Kịch bản agent-service: phát vài sự kiện tiến trình rồi một sự kiện {@code answer}. */
    private void agentReplies(String answerJson) {
        doAnswer(invocation -> {
                    Consumer<AgentClient.AgentEvent> onEvent = invocation.getArgument(1);
                    onEvent.accept(new AgentClient.AgentEvent("status", "{\"iteration\":0,\"message\":\"Dang suy luan\"}"));
                    onEvent.accept(new AgentClient.AgentEvent(
                            "tool_call", "{\"iteration\":0,\"name\":\"get_warning\",\"arguments\":{}}"));
                    onEvent.accept(new AgentClient.AgentEvent("answer", answerJson));
                    return null;
                })
                .when(agentClient)
                .analyze(any(), any());
    }

    private String answerCiting(String verdict, String... citedIds) {
        String ids = String.join("\",\"", citedIds);
        String evidenceIds = citedIds.length == 0 ? "[]" : "[\"" + ids + "\"]";
        return
                """
                {"answer":{"verdict":"%s","summary":"Tom tat cua mo hinh",
                 "claims":[{"text":"Mot nhan dinh","evidence_ids":%s,"confidence":0.8}],
                 "contradicting_evidence_ids":["EV-9999"],
                 "suggested_next_checks":["Kiem tra them"]},
                 "traces":[{"iteration":0,"provider":"scripted","model":"model-gia","prompt_version":"v1",
                 "input_tokens":100,"output_tokens":20,"latency_ms":5,"stop_reason":"end_turn",
                 "request_messages":[{"role":"user","content":"hoi"}],
                 "tool_calls":[{"name":"get_warning","duration_ms":3,"item_count":null}],
                 "response_content":"..."}]}
                """
                        .formatted(verdict, evidenceIds);
    }

    private ConversationResponse openConversation() {
        return conversationService.create(
                run.getId(), owner.getId(), new CreateConversationRequest(warningShortId, null));
    }

    /** Chờ lượt trả lời được ghi xong — {@code ask} chạy trên luồng nền. */
    private List<ChatMessageResponse> awaitMessages(UUID conversationId, int expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        List<ChatMessageResponse> messages = List.of();
        while (Instant.now().isBefore(deadline)) {
            messages = conversationService.messages(conversationId, owner.getId());
            if (messages.size() >= expected) {
                return messages;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return messages;
    }

    @Test
    void moPhienNeoVaoMotCanhBao() {
        ConversationResponse conversation = openConversation();

        assertThat(conversation.focusShortId()).isEqualTo(warningShortId);
        assertThat(conversation.analysisRunId()).isEqualTo(run.getId());
        assertThat(conversation.title()).contains(warningShortId);
    }

    /** Neo vào một Evidence ID không có trong run này phải hỏng NGAY lúc mở phiên. */
    @Test
    void neoVaoEvidenceKhongTonTaiThiHong() {
        assertThatThrownBy(() -> conversationService.create(
                        run.getId(), owner.getId(), new CreateConversationRequest("EV-FFFF", null)))
                .isInstanceOf(vn.hust.datn.evidence.exception.EvidenceNotFoundException.class);
    }

    /** Tài nguyên không thuộc về bạn ⇒ 404, không phải 403 (quy ước phân quyền của repo). */
    @Test
    void nguoiKhacKhongThayDuocPhien() {
        ConversationResponse conversation = openConversation();
        User nguoiLa = fixtures.user("nguoi-la-" + System.nanoTime() + "@x.test");

        assertThatThrownBy(() -> conversationService.messages(conversation.id(), nguoiLa.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trichDanDung_giuNguyenVerdict_vaGhiDuTrace() {
        agentReplies(answerCiting("LIKELY_TRUE_POSITIVE", warningShortId));
        ConversationResponse conversation = openConversation();

        conversationService.ask(conversation.id(), owner.getId(), "Canh bao nay co that khong?");
        List<ChatMessageResponse> messages = awaitMessages(conversation.id(), 2);

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().role()).isEqualTo(MessageRole.USER);

        ChatMessageResponse reply = messages.get(1);
        assertThat(reply.verdict()).isEqualTo(ClaimVerdict.LIKELY_TRUE_POSITIVE);
        assertThat(reply.downgradedFromVerdict()).isNull();
        assertThat(reply.suggestedNextChecks()).containsExactly("Kiem tra them");
        assertThat(reply.contradictingShortIds()).containsExactly("EV-9999");

        ChatMessageResponse.ClaimView claim = reply.claims().getFirst();
        assertThat(claim.verdict()).isEqualTo(ClaimVerdict.LIKELY_TRUE_POSITIVE);
        assertThat(claim.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.resolution()).isEqualTo(ClaimCitationResolution.RESOLVED);
            assertThat(citation.shortId()).isEqualTo(warningShortId);
        });

        Integer traceCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM llm_trace WHERE conversation_id = ? AND message_id IS NOT NULL",
                Integer.class,
                conversation.id());
        assertThat(traceCount).isEqualTo(1);
    }

    /**
     * Trọng tâm của cả ADR 0010: câu trả lời KHÔNG đi thẳng ra người dùng.
     *
     * <p>Mô hình tự tin kết luận {@code LIKELY_FALSE_POSITIVE} nhưng trích dẫn một ID bịa. Thứ
     * người dùng nhận phải là {@code INSUFFICIENT_EVIDENCE}, kèm verdict gốc để đo được số lần
     * hạ cấp — và nhận định vẫn còn nguyên vì nó là mẫu số của Unsupported Claim Rate.
     */
    @Test
    void trichDanBia_biHaCapTruocKhiToiNguoiDung() {
        agentReplies(answerCiting("LIKELY_FALSE_POSITIVE", "EV-FFFF"));
        ConversationResponse conversation = openConversation();

        conversationService.ask(conversation.id(), owner.getId(), "Canh bao nay sai phai khong?");
        ChatMessageResponse reply = awaitMessages(conversation.id(), 2).get(1);

        assertThat(reply.verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
        assertThat(reply.downgradedFromVerdict()).isEqualTo(ClaimVerdict.LIKELY_FALSE_POSITIVE);
        assertThat(reply.claims()).hasSize(1);
        assertThat(reply.claims().getFirst().citations().getFirst().resolution())
                .isEqualTo(ClaimCitationResolution.UNKNOWN_ID);
        assertThat(reply.claims().getFirst().citations().getFirst().shortId())
                .as("Khong duoc tao lien ket toi mot Evidence ID khong ton tai")
                .isNull();
    }

    /** Không nhận định nào có căn cứ ⇒ hạ cấp, kể cả khi mô hình rất chắc chắn. */
    @Test
    void khongCoTrichDanNao_biHaCap() {
        agentReplies(answerCiting("LIKELY_TRUE_POSITIVE"));
        ConversationResponse conversation = openConversation();

        conversationService.ask(conversation.id(), owner.getId(), "Sao nao?");
        ChatMessageResponse reply = awaitMessages(conversation.id(), 2).get(1);

        assertThat(reply.verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
        assertThat(reply.claims().getFirst().citations()).isEmpty();
    }

    /**
     * agent-service chết: lượt hỏi vẫn phải để lại dấu vết.
     *
     * <p>Không ghi gì thì "agent chết" và "chưa ai hỏi" nhìn giống hệt nhau trong DB, và người
     * vận hành không có gì để tra.
     */
    @Test
    void agentChet_vanGhiLaiDauVet() {
        doThrow(new vn.hust.datn.evidence.exception.AgentUnavailableException("connection refused"))
                .when(agentClient)
                .analyze(any(), any());
        ConversationResponse conversation = openConversation();

        conversationService.ask(conversation.id(), owner.getId(), "Hoi khi agent chet");

        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        Integer traceCount = 0;
        while (Instant.now().isBefore(deadline) && traceCount == 0) {
            traceCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM llm_trace WHERE conversation_id = ? AND message_id IS NULL",
                    Integer.class,
                    conversation.id());
            if (traceCount == 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        assertThat(traceCount).isEqualTo(1);
        // Câu hỏi vẫn còn; chỉ thiếu câu trả lời — người dùng hỏi lại được mà không mất ngữ cảnh.
        assertThat(conversationService.messages(conversation.id(), owner.getId())).hasSize(1);
    }

    /** Lịch sử phải đi kèm sang agent, nếu không mọi câu hỏi tiếp theo đều là câu đầu tiên. */
    @Test
    void luotThuHaiMangTheoLichSu() {
        agentReplies(answerCiting("LIKELY_TRUE_POSITIVE", warningShortId));
        ConversationResponse conversation = openConversation();

        conversationService.ask(conversation.id(), owner.getId(), "Cau hoi thu nhat");
        awaitMessages(conversation.id(), 2);
        conversationService.ask(conversation.id(), owner.getId(), "Cau hoi thu hai");
        List<ChatMessageResponse> messages = awaitMessages(conversation.id(), 4);

        assertThat(messages).hasSize(4);
        assertThat(messages).extracting(ChatMessageResponse::ordinal).containsExactly(0, 1, 2, 3);

        org.mockito.ArgumentCaptor<AgentClient.AnalyzeRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AgentClient.AnalyzeRequest.class);
        org.mockito.Mockito.verify(agentClient, org.mockito.Mockito.times(2)).analyze(captor.capture(), any());
        assertThat(captor.getAllValues().getFirst().history()).isEmpty();
        assertThat(captor.getAllValues().get(1).history()).hasSize(2);
    }
}
