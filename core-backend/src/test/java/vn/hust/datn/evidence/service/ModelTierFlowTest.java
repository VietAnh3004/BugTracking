package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AgentModel;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.ProjectTier;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.dto.ChatMessageResponse;
import vn.hust.datn.evidence.dto.ConversationResponse;
import vn.hust.datn.evidence.dto.CreateConversationRequest;
import vn.hust.datn.evidence.exception.PremiumModelNotAllowedException;
import vn.hust.datn.evidence.service.agent.AgentClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Quyền chọn model theo HẠNG DỰ ÁN. Điều cần giữ chắc là hướng sai nguy hiểm: một dự án Free mà
 * vẫn gọi được model Premium là tốn tiền thật cho người không được cấp quyền.
 *
 * <p>agent-service bị thay bằng spy (không gọi LLM thật); test ghi lại {@code AnalyzeRequest} thực
 * sự đi ra để khẳng định đúng {@code model_tier} tới tay agent-service.
 */
class ModelTierFlowTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private AgentClient agentClient;

    private User owner;
    private Project project;
    private AnalysisRun run;
    private final List<AgentClient.AnalyzeRequest> sent = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        sent.clear();
        owner = fixtures.user("chu-" + System.nanoTime() + "@x.test");
        project = fixtures.project("p-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
    }

    private void setTier(ProjectTier tier) {
        jdbc.update("UPDATE project SET tier = ? WHERE id = ?", tier.name(), project.getId());
    }

    /** agent-service giả: ghi lại request rồi trả một câu trả lời hợp lệ do {@code model} sinh ra. */
    private void agentAnswersWith(String model) {
        String answer =
                """
                {"answer":{"verdict":"INSUFFICIENT_EVIDENCE","summary":"Tom tat","claims":[],
                 "contradicting_evidence_ids":[],"suggested_next_checks":[]},
                 "traces":[{"iteration":0,"provider":"scripted","model":"%s","prompt_version":"v1",
                 "input_tokens":1,"output_tokens":1,"latency_ms":1,"stop_reason":"end_turn",
                 "request_messages":[],"tool_calls":[],"response_content":"..."}]}
                """
                        .formatted(model);
        doAnswer(invocation -> {
                    sent.add(invocation.getArgument(0));
                    Consumer<AgentClient.AgentEvent> onEvent = invocation.getArgument(1);
                    onEvent.accept(new AgentClient.AgentEvent("answer", answer));
                    return null;
                })
                .when(agentClient)
                .analyze(any(), any(), any());
    }

    private ConversationResponse open(User who) {
        return conversationService.create(run.getId(), who.getId(), new CreateConversationRequest(null, null));
    }

    private List<ChatMessageResponse> await(UUID conversationId, User who, int expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        List<ChatMessageResponse> messages = List.of();
        while (Instant.now().isBefore(deadline) && messages.size() < expected) {
            messages = conversationService.messages(conversationId, who.getId());
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
    void duAnMoiTaoLaFree() {
        assertThat(open(owner).projectTier()).isEqualTo(ProjectTier.FREE);
    }

    @Test
    void duAnFreeChonPremiumBi403VaKhongGoiAgentKhongGhiCauHoi() {
        ConversationResponse conversation = open(owner);

        assertThatThrownBy(() -> conversationService.ask(conversation.id(), owner.getId(), "hoi", AgentModel.PREMIUM))
                .isInstanceOf(PremiumModelNotAllowedException.class);

        verify(agentClient, never()).analyze(any(), any(), any());
        assertThat(conversationService.messages(conversation.id(), owner.getId()))
                .as("câu hỏi bị từ chối không được lưu")
                .isEmpty();
    }

    @Test
    void duAnFreeChonFreeVanChayVaGuiModelTierFree() {
        agentAnswersWith("gemini-test");
        ConversationResponse conversation = open(owner);

        conversationService.ask(conversation.id(), owner.getId(), "hoi", AgentModel.FREE);
        await(conversation.id(), owner, 2);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).modelTier()).isEqualTo("free");
    }

    @Test
    void duAnPremiumChonPremiumGuiModelTierPremiumVaGhiModelVaoTinTraLoi() {
        setTier(ProjectTier.PREMIUM);
        agentAnswersWith("claude-sonnet-5");
        ConversationResponse conversation = open(owner);
        assertThat(conversation.projectTier()).isEqualTo(ProjectTier.PREMIUM);

        conversationService.ask(conversation.id(), owner.getId(), "hoi", AgentModel.PREMIUM);
        List<ChatMessageResponse> messages = await(conversation.id(), owner, 2);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).modelTier()).isEqualTo("premium");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).model()).as("câu hỏi của người dùng không có model").isNull();
        assertThat(messages.get(1).model()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void duAnPremiumKhongChonModelThiMacDinhFreeChuKhongTuTonTien() {
        setTier(ProjectTier.PREMIUM);
        agentAnswersWith("gemini-test");
        ConversationResponse conversation = open(owner);

        conversationService.ask(conversation.id(), owner.getId(), "hoi", null);
        await(conversation.id(), owner, 2);

        assertThat(sent.get(0).modelTier()).isEqualTo("free");
    }

    @Test
    void thanhVienCuaDuAnPremiumCungDuocDungPremium() {
        setTier(ProjectTier.PREMIUM);
        agentAnswersWith("claude-sonnet-5");
        User member = fixtures.user("tv-" + System.nanoTime() + "@x.test");
        jdbc.update("INSERT INTO project_member (project_id, user_id) VALUES (?, ?)", project.getId(), member.getId());
        ConversationResponse conversation = open(member);

        conversationService.ask(conversation.id(), member.getId(), "hoi", AgentModel.PREMIUM);
        await(conversation.id(), member, 2);

        assertThat(sent.get(0).modelTier()).isEqualTo("premium");
    }

    @Test
    void haHangVeFreeCoHieuLucNgayCauSau() {
        setTier(ProjectTier.PREMIUM);
        agentAnswersWith("claude-sonnet-5");
        ConversationResponse conversation = open(owner);
        conversationService.ask(conversation.id(), owner.getId(), "cau mot", AgentModel.PREMIUM);
        await(conversation.id(), owner, 2);

        setTier(ProjectTier.FREE);

        assertThatThrownBy(() -> conversationService.ask(conversation.id(), owner.getId(), "cau hai", AgentModel.PREMIUM))
                .isInstanceOf(PremiumModelNotAllowedException.class);
    }

    @Test
    void quyenGanVoiDuAnKhongGanVoiNguoi() {
        // Cùng một người: chủ dự án Premium và chủ một dự án Free khác.
        setTier(ProjectTier.PREMIUM);
        Project freeProject = fixtures.project("free-" + System.nanoTime(), owner);
        AnalysisRun freeRun = fixtures.succeededRun(fixtures.snapshot(freeProject), 1);
        ConversationResponse inFree = conversationService.create(
                freeRun.getId(), owner.getId(), new CreateConversationRequest(null, null));

        assertThat(inFree.projectTier()).isEqualTo(ProjectTier.FREE);
        assertThatThrownBy(() -> conversationService.ask(inFree.id(), owner.getId(), "hoi", AgentModel.PREMIUM))
                .isInstanceOf(PremiumModelNotAllowedException.class);
    }
}
