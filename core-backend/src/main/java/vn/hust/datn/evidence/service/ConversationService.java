package vn.hust.datn.evidence.service;

import java.io.Closeable;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import vn.hust.datn.evidence.domain.AgentModel;
import vn.hust.datn.evidence.domain.Claim;
import vn.hust.datn.evidence.domain.ClaimCitation;
import vn.hust.datn.evidence.domain.Conversation;
import vn.hust.datn.evidence.domain.Message;
import vn.hust.datn.evidence.domain.MessageRole;
import vn.hust.datn.evidence.domain.ProjectTier;
import vn.hust.datn.evidence.dto.ChatMessageResponse;
import vn.hust.datn.evidence.dto.ConversationResponse;
import vn.hust.datn.evidence.dto.CreateConversationRequest;
import vn.hust.datn.evidence.exception.AgentUnavailableException;
import vn.hust.datn.evidence.exception.PremiumModelNotAllowedException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.repository.ClaimCitationRepository;
import vn.hust.datn.evidence.repository.ClaimRepository;
import vn.hust.datn.evidence.repository.ConversationRepository;
import vn.hust.datn.evidence.repository.MessageRepository;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.service.agent.AgentAnswer;
import vn.hust.datn.evidence.service.agent.AgentClient;
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
    private final ProjectRepository projectRepository;
    private final AgentClient agentClient;
    private final CitationValidator citationValidator;
    private final Executor agentExecutor;

    /**
     * Lượt hỏi đang chạy, theo hội thoại. Trong bộ nhớ là đủ: một lượt chỉ sống trong một tiến
     * trình JVM, và JVM chết thì kết nối tới agent-service cũng chết theo.
     */
    private final ConcurrentMap<UUID, ActiveTurn> activeTurns = new ConcurrentHashMap<>();

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
            ProjectRepository projectRepository,
            AgentClient agentClient,
            CitationValidator citationValidator,
            @Qualifier("agentExecutor") Executor agentExecutor) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.claimRepository = claimRepository;
        this.claimCitationRepository = claimCitationRepository;
        this.persistence = persistence;
        this.evidenceQueryService = evidenceQueryService;
        this.projectRepository = projectRepository;
        this.agentClient = agentClient;
        this.citationValidator = citationValidator;
        this.agentExecutor = agentExecutor;
    }

    @Transactional
    public ConversationResponse create(UUID runId, UUID ownerId, CreateConversationRequest request) {
        evidenceQueryService.requireRunReadableBy(runId, ownerId);
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
        return ConversationResponse.from(conversationRepository.save(conversation), tierOf(runId));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunReadableBy(runId, ownerId);
        ProjectTier tier = tierOf(runId);
        return conversationRepository.findByAnalysisRunIdAndOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(runId, ownerId).stream()
                .map(conversation -> ConversationResponse.from(conversation, tier))
                .toList();
    }

    /**
     * Đổi tên phiên. Không đụng {@code updatedAt}: danh sách sắp theo lần hoạt động gần nhất, mà đổi
     * tên không phải hoạt động hỏi đáp — nếu nhảy lên đầu thì phiên vừa sửa tên "nhảy" khỏi chỗ cũ.
     */
    @Transactional
    public ConversationResponse rename(UUID conversationId, UUID ownerId, String title) {
        Conversation conversation = requireOwned(conversationId, ownerId);
        conversation.setTitle(title.strip());
        return ConversationResponse.from(conversationRepository.save(conversation), tierOf(conversation.getAnalysisRunId()));
    }

    /**
     * Xoá phiên = ẨN (V15), giữ nguyên dữ liệu để đo thực nghiệm. Lượt đang chạy bị huỷ trước: nếu
     * không, câu trả lời của nó vẫn được ghi vào một phiên người dùng đã thấy biến mất.
     */
    @Transactional
    public void delete(UUID conversationId, UUID ownerId) {
        Conversation conversation = requireOwned(conversationId, ownerId);
        ActiveTurn turn = activeTurns.get(conversationId);
        if (turn != null) {
            turn.cancel();
        }
        conversation.setDeletedAt(OffsetDateTime.now());
        conversationRepository.save(conversation);
        log.info("nguoi dung {} xoa (an) hoi thoai {}", ownerId, conversationId);
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
        return ask(conversationId, ownerId, question, AgentModel.FREE);
    }

    /**
     * @param model model người dùng chọn cho câu này; null = FREE. PREMIUM mà dự án chưa Premium thì
     *     ném {@link PremiumModelNotAllowedException} (403) TRƯỚC khi mở luồng SSE và trước khi ghi
     *     câu hỏi — hạng đọc lại ở mỗi câu nên admin hạ hạng là có hiệu lực ngay.
     */
    public SseEmitter ask(UUID conversationId, UUID ownerId, String question, AgentModel model) {
        Conversation conversation = requireOwned(conversationId, ownerId);
        AgentModel chosen = model == null ? AgentModel.FREE : model;
        if (chosen == AgentModel.PREMIUM && tierOf(conversation.getAnalysisRunId()) != ProjectTier.PREMIUM) {
            throw new PremiumModelNotAllowedException();
        }
        List<Map<String, String>> history = historyOf(conversation.getId());
        persistence.appendUserMessage(conversation.getId(), question);

        // Timeout của emitter phải DÀI HƠN timeout gọi agent, nếu không trình duyệt bị ngắt
        // trước khi câu trả lời kịp tới và người dùng thấy lượt hỏi biến mất không dấu vết.
        SseEmitter emitter = new SseEmitter(0L);

        // Hỏi câu mới trong cùng hội thoại thì huỷ lượt cũ — hai lượt cùng ghi vào một hội thoại
        // sẽ xen kẽ câu trả lời, và lượt cũ người dùng đã bỏ đi thì không đáng tốn thêm quota.
        ActiveTurn turn = new ActiveTurn();
        ActiveTurn previous = activeTurns.put(conversation.getId(), turn);
        if (previous != null) {
            previous.cancel();
        }
        // Trình duyệt ngắt kết nối (đóng tab, rời trang) mà container phát hiện được: huỷ luôn.
        emitter.onError(error -> turn.cancel());

        agentExecutor.execute(() -> stream(emitter, conversation, question, history, chosen, turn));
        return emitter;
    }

    /**
     * Huỷ lượt hỏi đang chạy của một hội thoại, nếu có. Không có lượt nào thì không làm gì —
     * huỷ hai lần, hay huỷ đúng lúc lượt vừa xong, đều không phải lỗi.
     */
    public void cancel(UUID conversationId, UUID ownerId) {
        requireOwned(conversationId, ownerId);
        ActiveTurn turn = activeTurns.get(conversationId);
        if (turn != null) {
            log.info("nguoi dung huy luot hoi cua hoi thoai {}", conversationId);
            turn.cancel();
        }
    }

    private void stream(
            SseEmitter emitter,
            Conversation conversation,
            String question,
            List<Map<String, String>> history,
            AgentModel model,
            ActiveTurn turn) {
        List<AgentAnswer.Trace> traces = new ArrayList<>();
        try {
            agentClient.analyze(
                    AgentClient.AnalyzeRequest.of(
                            conversation.getAnalysisRunId(), question, conversation.getFocusShortId(), history, model),
                    event -> handle(emitter, conversation, traces, event, turn),
                    turn::attach);
            emitter.complete();
        } catch (AgentUnavailableException e) {
            if (turn.isCancelled()) {
                // Huỷ có chủ đích: kết nối bị chính ta đóng nên readLine ném — không phải sự cố
                // của agent-service, không ghi trace hỏng, không hiện thông báo đỏ.
                send(emitter, "cancelled", Map.of("message", "Đã dừng lượt hỏi."), turn);
                emitter.complete();
                return;
            }
            log.warn("agent-service khong dung duoc cho hoi thoai {}: {}", conversation.getId(), e.getMessage());
            persistence.persistFailedTraces(conversation.getId(), traces, e.getMessage());
            sendError(emitter, e.getMessage());
        } catch (RuntimeException e) {
            log.error("loi khong luong truoc khi tra loi hoi thoai {}", conversation.getId(), e);
            persistence.persistFailedTraces(conversation.getId(), traces, e.toString());
            sendError(emitter, "Loi noi bo khi goi Agent");
        } finally {
            activeTurns.remove(conversation.getId(), turn);
        }
    }

    private void handle(
            SseEmitter emitter,
            Conversation conversation,
            List<AgentAnswer.Trace> traces,
            AgentClient.AgentEvent event,
            ActiveTurn turn) {

        switch (event.name()) {
            case AgentClient.AgentEvent.ANSWER -> {
                AgentAnswer raw = agentClient.parse(event, AgentAnswer.class);
                traces.clear();
                traces.addAll(raw.tracesOrEmpty());

                ValidatedAnswer validated =
                        citationValidator.validate(conversation.getAnalysisRunId(), raw.answer().toDraft());
                ChatMessageResponse saved = persistence.persistAnswer(conversation, validated, raw.tracesOrEmpty());
                send(emitter, "answer", saved, turn);
            }
            case AgentClient.AgentEvent.ERROR -> {
                // Lượt hỏng: agent-service đã kèm trace tới thời điểm chết trong payload.
                ErrorPayload payload = parseError(event);
                traces.clear();
                traces.addAll(payload.tracesOrEmpty());
                // Chỉ chuyển câu giải thích, KHÔNG chuyển nguyên payload: trước đây cả mảng trace
                // đi thẳng ra giao diện dưới dạng một chuỗi JSON đã bị thay dấu nháy.
                throw new AgentUnavailableException(payload.messageOr(event.data()));
            }
            // status/tool_call/tool_result forward nguyên văn: chúng chỉ là tiến trình, không
            // chứa kết luận nào cần hậu kiểm.
            // Chuyển tiếp nhịp xuống trình duyệt dưới dạng chú thích SSE: trong lúc chờ một lời gọi
            // LLM dài, Nginx trên VPS (proxy_read_timeout) không thấy gì và sẽ cắt kết nối.
            case AgentClient.AgentEvent.HEARTBEAT -> sendHeartbeat(emitter, turn);
            default -> sendRaw(emitter, event.name(), event.data(), turn);
        }
    }

    private ErrorPayload parseError(AgentClient.AgentEvent event) {
        try {
            return agentClient.parse(event, ErrorPayload.class);
        } catch (RuntimeException e) {
            return new ErrorPayload(null, null);
        }
    }

    private record ErrorPayload(String message, List<AgentAnswer.Trace> traces) {

        List<AgentAnswer.Trace> tracesOrEmpty() {
            return traces == null ? List.of() : traces;
        }

        String messageOr(String fallback) {
            return message == null || message.isBlank() ? fallback : message;
        }
    }

    private void send(SseEmitter emitter, String name, Object payload) {
        send(emitter, name, payload, null);
    }

    /**
     * Gửi một sự kiện; trình duyệt đã ngắt thì HUỶ lượt đang chạy (nếu có {@code turn}).
     *
     * <p>Đây là chỗ phát hiện "người dùng đã bỏ đi" chắc chắn nhất: servlet container thường chỉ
     * biết client ngắt khi thử GHI. Huỷ ngay thay vì đọc tiếp từ agent-service — đọc tiếp nghĩa
     * là tiếp tục gọi LLM, tốn quota cho một câu trả lời không ai nhận.
     */
    private void send(SseEmitter emitter, String name, Object payload, ActiveTurn turn) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException | IllegalStateException e) {
            clientGone(name, turn);
        }
    }

    private void sendRaw(SseEmitter emitter, String name, String json, ActiveTurn turn) {
        try {
            emitter.send(SseEmitter.event().name(name).data(json, org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            clientGone(name, turn);
        }
    }

    private void sendHeartbeat(SseEmitter emitter, ActiveTurn turn) {
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (IOException | IllegalStateException e) {
            clientGone("heartbeat", turn);
        }
    }

    private static void clientGone(String eventName, ActiveTurn turn) {
        log.debug("client dong ket noi SSE truoc khi nhan {}", eventName);
        if (turn != null) {
            turn.cancel();
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        // Để Jackson dựng JSON. Ghép chuỗi + thay dấu nháy hỏng ngay khi thông điệp có dấu `\`
        // hay xuống dòng — lỗi trong lúc báo lỗi, người dùng thấy một luồng SSE câm.
        send(emitter, "error", Map.of("message", message));
        emitter.complete();
    }

    /**
     * Một lượt hỏi đang chạy. {@link #cancel} có thể tới TRƯỚC khi kết nối mở (bấm Dừng ngay sau
     * khi gửi) — khi đó {@link #attach} đóng kết nối ngay lúc nó vừa tới.
     */
    static final class ActiveTurn {
        private volatile boolean cancelled;
        private volatile Closeable connection;

        void attach(Closeable newConnection) {
            this.connection = newConnection;
            if (cancelled) {
                closeQuietly(newConnection);
            }
        }

        void cancel() {
            cancelled = true;
            Closeable current = connection;
            if (current != null) {
                closeQuietly(current);
            }
        }

        boolean isCancelled() {
            return cancelled;
        }

        private static void closeQuietly(Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException | RuntimeException e) {
                log.debug("dong ket noi agent-service khi huy: {}", e.toString());
            }
        }
    }

    private Conversation requireOwned(UUID conversationId, UUID ownerId) {
        return conversationRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(conversationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("conversation", conversationId));
    }

    /** Hạng dự án chứa run; run không có dự án (không xảy ra) coi là FREE — hướng an toàn. */
    private ProjectTier tierOf(UUID runId) {
        return projectRepository.findTierByRunId(runId).orElse(ProjectTier.FREE);
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
                    message.getCreatedAt(),
                    null);
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
                message.getCreatedAt(),
                message.getModel());
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
