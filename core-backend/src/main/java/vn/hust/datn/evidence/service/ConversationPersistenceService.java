package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.Claim;
import vn.hust.datn.evidence.domain.ClaimCitation;
import vn.hust.datn.evidence.domain.Conversation;
import vn.hust.datn.evidence.domain.LlmTrace;
import vn.hust.datn.evidence.domain.Message;
import vn.hust.datn.evidence.domain.MessageRole;
import vn.hust.datn.evidence.dto.ChatMessageResponse;
import vn.hust.datn.evidence.repository.ClaimCitationRepository;
import vn.hust.datn.evidence.repository.ClaimRepository;
import vn.hust.datn.evidence.repository.ConversationRepository;
import vn.hust.datn.evidence.repository.LlmTraceRepository;
import vn.hust.datn.evidence.repository.MessageRepository;
import vn.hust.datn.evidence.service.agent.AgentAnswer;
import vn.hust.datn.evidence.service.citation.ShortIdFormat;
import vn.hust.datn.evidence.service.citation.ValidatedAnswer;

import lombok.RequiredArgsConstructor;

/**
 * Các thao tác DB của một lượt hỏi đáp, MỖI THAO TÁC MỘT TRANSACTION RIÊNG.
 *
 * <p>Tách class là BẮT BUỘC chứ không phải để gọn: {@link ConversationService#ask} chạy một lời
 * gọi mạng dài hàng chục giây, nên nó KHÔNG được nằm trong transaction nào. Nếu các method dưới
 * đây nằm chung class và được gọi bằng {@code this.persistAnswer(...)} thì đó là self-invocation
 * — không đi qua Spring AOP proxy — nên {@code @Transactional} bị bỏ qua hoàn toàn: không lỗi,
 * không cảnh báo, chỉ lặng lẽ ghi ngoài transaction. Cùng khuôn với
 * {@code AnalysisRunPersistenceService}.
 */
@Service
@RequiredArgsConstructor
public class ConversationPersistenceService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ClaimRepository claimRepository;
    private final ClaimCitationRepository claimCitationRepository;
    private final LlmTraceRepository llmTraceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Message appendUserMessage(UUID conversationId, String question) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setOrdinal(messageRepository.nextOrdinal(conversationId));
        message.setRole(MessageRole.USER);
        message.setContent(question);
        message.setCreatedAt(OffsetDateTime.now());
        return messageRepository.saveAndFlush(message);
    }

    /**
     * Ghi câu trả lời ĐÃ hậu kiểm, cùng nhận định, trích dẫn và nhật ký gọi model — một transaction.
     *
     * <p>Cùng một transaction là có chủ đích: không được tồn tại trạng thái "đã có câu trả lời
     * nhưng chưa có trace" hay ngược lại. Trace là nguồn số liệu chương 4 và không tái tạo được.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessageResponse persistAnswer(
            Conversation conversation, ValidatedAnswer validated, List<AgentAnswer.Trace> traces) {

        OffsetDateTime now = OffsetDateTime.now();

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setOrdinal(messageRepository.nextOrdinal(conversation.getId()));
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(validated.summary());
        message.setVerdict(validated.verdict());
        message.setDowngradedFromVerdict(validated.downgradedFrom());
        message.setSuggestedNextChecks(List.copyOf(validated.suggestedNextChecks()));
        message.setContradictingShortIds(List.copyOf(validated.contradictingShortIds()));
        message.setCreatedAt(now);
        Message saved = messageRepository.saveAndFlush(message);

        List<ChatMessageResponse.ClaimView> claimViews = new ArrayList<>(validated.claims().size());
        int ordinal = 0;
        for (ValidatedAnswer.ValidatedClaim validatedClaim : validated.claims()) {
            Claim claim = new Claim();
            claim.setMessageId(saved.getId());
            claim.setOrdinal(ordinal);
            claim.setText(validatedClaim.text());
            claim.setVerdict(validatedClaim.verdict());
            claim.setDowngradedFromVerdict(validatedClaim.downgradedFrom());
            claim.setConfidence(validatedClaim.confidence());
            claim.setCreatedAt(now);
            Claim savedClaim = claimRepository.saveAndFlush(claim);

            List<ChatMessageResponse.CitationView> citationViews =
                    new ArrayList<>(validatedClaim.citations().size());
            for (ValidatedAnswer.ResolvedCitation citation : validatedClaim.citations()) {
                ClaimCitation row = new ClaimCitation();
                row.setClaimId(savedClaim.getId());
                row.setCitedShortIdRaw(citation.citedShortIdRaw());
                row.setEvidenceRecordId(citation.evidenceRecordId());
                row.setResolution(citation.resolution());
                row.setCreatedAt(now);
                claimCitationRepository.save(row);
                citationViews.add(toView(citation));
            }

            claimViews.add(new ChatMessageResponse.ClaimView(
                    ordinal,
                    validatedClaim.text(),
                    validatedClaim.verdict(),
                    validatedClaim.downgradedFrom(),
                    validatedClaim.confidence(),
                    List.copyOf(citationViews)));
            ordinal++;
        }

        saveTraces(conversation.getId(), saved.getId(), traces, now);

        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        return new ChatMessageResponse(
                saved.getId(),
                saved.getOrdinal(),
                MessageRole.ASSISTANT,
                saved.getContent(),
                saved.getVerdict(),
                saved.getDowngradedFromVerdict(),
                saved.getSuggestedNextChecks(),
                saved.getContradictingShortIds(),
                List.copyOf(claimViews),
                saved.getCreatedAt());
    }

    /**
     * Ghi nhật ký của một lượt HỎNG — chưa có {@code message} nào để gắn vào.
     *
     * <p>Chính những dòng này cho biết agent chết ở vòng thứ mấy, thứ không tái tạo được sau. Bỏ
     * qua chúng là bỏ đúng phần dữ liệu khó có nhất của chương 4.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFailedTraces(UUID conversationId, List<AgentAnswer.Trace> traces, String errorMessage) {
        OffsetDateTime now = OffsetDateTime.now();
        List<AgentAnswer.Trace> effective = traces;
        if (effective.isEmpty()) {
            // Hỏng trước cả vòng gọi model đầu tiên (agent-service chết, mạng đứt). Vẫn phải để
            // lại một dòng: "không có trace nào" và "chưa từng hỏi" nhìn giống hệt nhau trong DB.
            effective = List.of(new AgentAnswer.Trace(
                    0, "unknown", "unknown", "unknown", 0, 0, 0, null, List.of(), List.of(), null, errorMessage));
        }
        saveTraces(conversationId, null, effective, now);
    }

    private void saveTraces(
            UUID conversationId, UUID messageId, List<AgentAnswer.Trace> traces, OffsetDateTime now) {
        for (AgentAnswer.Trace trace : traces) {
            LlmTrace row = new LlmTrace();
            row.setConversationId(conversationId);
            row.setMessageId(messageId);
            row.setIteration(trace.iteration());
            row.setProvider(trace.provider());
            row.setModel(trace.model());
            row.setPromptVersion(trace.promptVersion());
            row.setInputTokens(trace.inputTokens());
            row.setOutputTokens(trace.outputTokens());
            row.setLatencyMs(trace.latencyMs());
            row.setStopReason(trace.stopReason());
            row.setRequestMessages(trace.requestMessagesOrEmpty());
            row.setToolCalls(trace.toolCallsOrEmpty());
            row.setResponseContent(trace.responseContent());
            row.setErrorMessage(trace.errorMessage());
            row.setCreatedAt(now);
            llmTraceRepository.save(row);
        }
    }

    private static ChatMessageResponse.CitationView toView(ValidatedAnswer.ResolvedCitation citation) {
        // shortId chỉ điền khi tra được: giao diện dùng nó để mở Evidence Explorer, và một liên
        // kết trỏ tới ID không tồn tại còn tệ hơn không có liên kết.
        String shortId = citation.isResolved()
                ? ShortIdFormat.canonical(citation.citedShortIdRaw()).orElse(null)
                : null;
        return new ChatMessageResponse.CitationView(citation.citedShortIdRaw(), citation.resolution(), shortId);
    }
}
