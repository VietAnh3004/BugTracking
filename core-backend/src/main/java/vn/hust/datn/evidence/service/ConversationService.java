package vn.hust.datn.evidence.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import vn.hust.datn.evidence.domain.Claim;
import vn.hust.datn.evidence.domain.ClaimCitation;
import vn.hust.datn.evidence.domain.Conversation;
import vn.hust.datn.evidence.domain.Message;
import vn.hust.datn.evidence.domain.MessageRole;
import vn.hust.datn.evidence.dto.ChatMessageResponse;
import vn.hust.datn.evidence.dto.ConversationResponse;
import vn.hust.datn.evidence.dto.CreateConversationRequest;
import vn.hust.datn.evidence.exception.AgentUnavailableException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.repository.ClaimCitationRepository;
import vn.hust.datn.evidence.repository.ClaimRepository;
import vn.hust.datn.evidence.repository.ConversationRepository;
import vn.hust.datn.evidence.repository.MessageRepository;
import vn.hust.datn.evidence.service.agent.AgentAnswer;
import vn.hust.datn.evidence.service.agent.AgentClient;
import vn.hust.datn.evidence.service.citation.AnswerDraft;
import vn.hust.datn.evidence.service.citation.CitationValidator;
import vn.hust.datn.evidence.service.citation.ShortIdFormat;
import vn.hust.datn.evidence.service.citation.ValidatedAnswer;

import lombok.extern.slf4j.Slf4j;

/**
 * Điều phối một lượt hỏi đáp: gọi agent-service, hậu kiểm trích dẫn, ghi DB, phát ra frontend.
 *
 * <p><b>Câu trả lời chỉ ra khỏi đây SAU khi qua {@link CitationValidator}.</b> Frontend không bao
 * giờ nhận sự kiện {@code answer} nguyên bản của mô hình — nếu nhận được thì bước hậu kiểm chỉ là
 * trang trí, vì người dùng đã đọc kết luận chưa kiểm trước khi nó bị hạ cấp (ADR 0010).
 *
 * <p>Lớp này KHÔNG mang {@code @Transactional} trên đường {@link #ask}: nó chờ một lời gọi mạng
 * dài hàng chục giây, và giữ một kết nối DB suốt thời gian đó sẽ vét cạn pool khi có vài người
 * dùng cùng lúc. Mọi thao tác ghi đi qua {@link ConversationPersistenceService}.
 */
@Slf4j
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ClaimRepository claimRepository;
    private final ClaimCitationRepository claimCitationRepository;
    private final ConversationPersistenceService persistence;
    private final EvidenceQueryService evidenceQueryService;
    private final AgentClient agentClient;
    private final CitationValidator citationValidator;
    private final Executor agentExecutor;

    // Constructor viết tay chứ không dùng @RequiredArgsConstructor: Lombok KHÔNG chép
    // @Qualifier sang tham số constructor, nên với hai bean Executor trong context
    // (agentExecutor và sandboxExecutor) việc tiêm sẽ phụ thuộc vào trùng tên trường — một
    // ràng buộc ngầm mà đổi tên biến là hỏng. Cùng khuôn với AnalysisRunService.
    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ClaimRepository claimRepository,
            ClaimCitationRepository claimCitationRepository,
            ConversationPersistenceService persistence,
            EvidenceQueryService evidenceQueryService,
            AgentClient agentClient,
            CitationValidator citationValidator,
            @Qualifier("agentExecutor") Executor agentExecutor) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.claimRepository = claimRepository;
        this.claimCitationRepository = claimCitationRepository;
        this.persistence = persistence;
        this.evidenceQueryService = evidenceQueryService;
        this.agentClient = agentClient;
        this.citationValidator = citationValidator;
        this.agentExecutor = agentExecutor;
    }

    @Transactional
    public ConversationResponse create(UUID runId, UUID ownerId, CreateConversationRequest request) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        // Bắt run phải truy vấn được NGAY khi mở phiên, không đợi tới câu hỏi đầu tiên: hỏi về
        // một run đang chạy thì mọi Evidence ID còn chưa tồn tại.
        evidenceQueryService.ensureRunQueryable(runId);

        // Khuôn dạng đã được @Pattern trên DTO kiểm; ở đây chỉ chuẩn hoá về chữ hoa.
        String focus = request.focusShortId() == null
                ? null
                : ShortIdFormat.canonical(request.focusShortId()).orElseThrow();
        if (focus != null) {
            // Tồn tại trong ĐÚNG run này — ném 404 phân loại của EvidenceQueryService nếu không.
            evidenceQueryService.getEvidence(runId, focus);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setOwnerId(ownerId);
        conversation.setAnalysisRunId(runId);
        conversation.setFocusShortId(focus);
        conversation.setTitle(title(request.title(), focus));
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        return conversationRepository.findByAnalysisRunIdAndOwnerIdOrderByUpdatedAtDesc(runId, ownerId).stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(UUID conversationId, UUID ownerId) {
        Conversation conversation = requireOwned(conversationId, ownerId);
        return messageRepository.findByConversationIdOrderByOrdinal(conversation.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Hỏi một câu và stream tiến trình về trình duyệt.
     *
     * <p>Chạy trên luồng nền: giữ luồng của servlet container suốt một lượt suy luận hàng chục
     * giây sẽ làm cạn pool luồng của Tomcat trước khi cạn bất cứ thứ gì khác.
     */
    public SseEmitter ask(UUID conversationId, UUID ownerId, String question) {
        Conversation conversation = requireOwned(conversationId, ownerId);
        List<Map<String, String>> history = historyOf(conversation.getId());
        persistence.appendUserMessage(conversation.getId(), question);

        // Timeout của emitter phải DÀI HƠN timeout gọi agent, nếu không trình duyệt bị ngắt
        // trước khi câu trả lời kịp tới và người dùng thấy lượt hỏi biến mất không dấu vết.
        SseEmitter emitter = new SseEmitter(0L);
        agentExecutor.execute(() -> stream(emitter, conversation, question, history));
        return emitter;
    }

    private void stream(
            SseEmitter emitter, Conversation conversation, String question, List<Map<String, String>> history) {
        List<AgentAnswer.Trace> traces = new ArrayList<>();
        try {
            agentClient.analyze(
                    AgentClient.AnalyzeRequest.of(
                            conversation.getAnalysisRunId(), question, conversation.getFocusShortId(), history),
                    event -> handle(emitter, conversation, traces, event));
            emitter.complete();
        } catch (AgentUnavailableException e) {
            log.warn("agent-service khong dung duoc cho hoi thoai {}: {}", conversation.getId(), e.getMessage());
            persistence.persistFailedTraces(conversation.getId(), traces, e.getMessage());
            sendError(emitter, e.getMessage());
        } catch (RuntimeException e) {
            log.error("loi khong luong truoc khi tra loi hoi thoai {}", conversation.getId(), e);
            persistence.persistFailedTraces(conversation.getId(), traces, e.toString());
            sendError(emitter, "Loi noi bo khi goi Agent");
        }
    }

    private void handle(
            SseEmitter emitter,
            Conversation conversation,
            List<AgentAnswer.Trace> traces,
            AgentClient.AgentEvent event) {

        switch (event.name()) {
            case AgentClient.AgentEvent.ANSWER -> {
                AgentAnswer raw = agentClient.parse(event, AgentAnswer.class);
                traces.clear();
                traces.addAll(raw.tracesOrEmpty());

                ValidatedAnswer validated =
                        citationValidator.validate(conversation.getAnalysisRunId(), toDraft(raw.answer()));
                ChatMessageResponse saved = persistence.persistAnswer(conversation, validated, raw.tracesOrEmpty());
                send(emitter, "answer", saved);
            }
            case AgentClient.AgentEvent.ERROR -> {
                // Lượt hỏng: agent-service đã kèm trace tới thời điểm chết trong payload.
                AgentAnswer.Trace[] partial = parseErrorTraces(event);
                traces.clear();
                traces.addAll(List.of(partial));
                throw new AgentUnavailableException(event.data());
            }
            // status/tool_call/tool_result forward nguyên văn: chúng chỉ là tiến trình, không
            // chứa kết luận nào cần hậu kiểm.
            default -> sendRaw(emitter, event.name(), event.data());
        }
    }

    private AgentAnswer.Trace[] parseErrorTraces(AgentClient.AgentEvent event) {
        try {
            record ErrorPayload(String message, List<AgentAnswer.Trace> traces) {}
            ErrorPayload payload = agentClient.parse(event, ErrorPayload.class);
            return payload.traces() == null ? new AgentAnswer.Trace[0] : payload.traces().toArray(AgentAnswer.Trace[]::new);
        } catch (RuntimeException e) {
            return new AgentAnswer.Trace[0];
        }
    }

    private static AnswerDraft toDraft(AgentAnswer.Answer answer) {
        List<AnswerDraft.ClaimDraft> claims = answer.claimsOrEmpty().stream()
                .map(c -> new AnswerDraft.ClaimDraft(c.text(), c.evidenceIdsOrEmpty(), c.confidence()))
                .toList();
        return new AnswerDraft(
                answer.verdict(), answer.summary(), claims, answer.contradictingOrEmpty(), answer.suggestedOrEmpty());
    }

    private void send(SseEmitter emitter, String name, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException e) {
            // Trình duyệt đóng tab giữa chừng. Không phải lỗi: dữ liệu đã ghi xong vào DB rồi.
            log.debug("client dong ket noi SSE truoc khi nhan {}", name);
        }
    }

    private void sendRaw(SseEmitter emitter, String name, String json) {
        try {
            emitter.send(SseEmitter.event().name(name).data(json, org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.debug("client dong ket noi SSE truoc khi nhan {}", name);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        sendRaw(emitter, "error", "{\"message\":\"%s\"}".formatted(message.replace("\"", "'")));
        emitter.complete();
    }

    private Conversation requireOwned(UUID conversationId, UUID ownerId) {
        return conversationRepository
                .findByIdAndOwnerId(conversationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("conversation", conversationId));
    }

    /** Lịch sử gửi cho agent: chỉ vai và nội dung, không kèm nhận định hay trích dẫn. */
    private List<Map<String, String>> historyOf(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByOrdinal(conversationId).stream()
                .map(m -> Map.of("role", m.getRole().name(), "content", m.getContent()))
                .toList();
    }

    private ChatMessageResponse toResponse(Message message) {
        if (message.getRole() == MessageRole.USER) {
            return new ChatMessageResponse(
                    message.getId(),
                    message.getOrdinal(),
                    MessageRole.USER,
                    message.getContent(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    message.getCreatedAt());
        }

        List<ChatMessageResponse.ClaimView> claims = claimRepository.findByMessageIdOrderByOrdinal(message.getId()).stream()
                .map(this::toClaimView)
                .toList();

        return new ChatMessageResponse(
                message.getId(),
                message.getOrdinal(),
                MessageRole.ASSISTANT,
                message.getContent(),
                message.getVerdict(),
                message.getDowngradedFromVerdict(),
                message.getSuggestedNextChecks(),
                message.getContradictingShortIds(),
                claims,
                message.getCreatedAt());
    }

    private ChatMessageResponse.ClaimView toClaimView(Claim claim) {
        List<ChatMessageResponse.CitationView> citations = claimCitationRepository.findByClaimId(claim.getId()).stream()
                .map(ConversationService::toCitationView)
                .toList();
        return new ChatMessageResponse.ClaimView(
                claim.getOrdinal(),
                claim.getText(),
                claim.getVerdict(),
                claim.getDowngradedFromVerdict(),
                claim.getConfidence(),
                citations);
    }

    private static ChatMessageResponse.CitationView toCitationView(ClaimCitation citation) {
        String shortId = citation.getEvidenceRecordId() == null
                ? null
                : ShortIdFormat.canonical(citation.getCitedShortIdRaw()).orElse(null);
        return new ChatMessageResponse.CitationView(
                citation.getCitedShortIdRaw(), citation.getResolution(), shortId);
    }

    private static String title(String requested, String focus) {
        if (requested != null && !requested.isBlank()) {
            return requested.strip();
        }
        return focus == null ? "Hỏi về lần phân tích" : "Hỏi về " + focus;
    }
}
