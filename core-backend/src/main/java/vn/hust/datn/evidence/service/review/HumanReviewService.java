package vn.hust.datn.evidence.service.review;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.Conversation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.HumanReview;
import vn.hust.datn.evidence.domain.Message;
import vn.hust.datn.evidence.domain.MessageRole;
import vn.hust.datn.evidence.dto.ReviewResponse;
import vn.hust.datn.evidence.dto.SubmitReviewRequest;
import vn.hust.datn.evidence.exception.InvalidReviewException;
import vn.hust.datn.evidence.repository.ConversationRepository;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;
import vn.hust.datn.evidence.repository.HumanReviewRepository;
import vn.hust.datn.evidence.repository.MessageRepository;
import vn.hust.datn.evidence.service.EvidenceQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Ghi nhận và đọc lại quyết định của người dùng về một cảnh báo.
 *
 * <p>Neo vào cảnh báo chứ không vào câu trả lời của Agent (ADR 0011): user study ở tuần 16 có một
 * nhóm KHÔNG dùng Agent, và nhóm đó phải có chỗ để ghi.
 */
@Service
@RequiredArgsConstructor
public class HumanReviewService {

    private final HumanReviewRepository humanReviewRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final EvidenceQueryService evidenceQueryService;

    /**
     * Ghi hoặc cập nhật quyết định — idempotent theo {@code (cảnh báo, người review)}.
     *
     * <p>{@code createdAt} giữ nguyên khi cập nhật: nó là lúc người đó quyết định LẦN ĐẦU, và đó
     * mới là thứ user study quan tâm. Ghi đè nó khi đổi ý sẽ xoá mất mốc thời gian thật.
     */
    @Transactional
    public ReviewResponse submit(UUID runId, String shortId, UUID reviewerId, SubmitReviewRequest request) {
        evidenceQueryService.requireRunOwnedBy(runId, reviewerId);
        EvidenceRecord evidence = evidenceQueryService.getEvidence(runId, shortId);

        UUID messageId = validateMessage(request.messageId(), runId, reviewerId);
        OffsetDateTime now = OffsetDateTime.now();

        HumanReview review = humanReviewRepository
                .findByEvidenceRecordIdAndReviewerId(evidence.getId(), reviewerId)
                .orElseGet(() -> {
                    HumanReview fresh = new HumanReview();
                    fresh.setAnalysisRunId(runId);
                    fresh.setEvidenceRecordId(evidence.getId());
                    fresh.setReviewerId(reviewerId);
                    fresh.setCreatedAt(now);
                    return fresh;
                });

        review.setDecision(request.decision());
        review.setMessageId(messageId);
        review.setNote(request.note());
        review.setSecondsSpent(request.secondsSpent());
        review.setUpdatedAt(now);

        return ReviewResponse.from(humanReviewRepository.save(review), evidence.getShortId());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listMine(UUID runId, UUID reviewerId) {
        evidenceQueryService.requireRunOwnedBy(runId, reviewerId);

        List<HumanReview> reviews =
                humanReviewRepository.findByAnalysisRunIdAndReviewerIdOrderByUpdatedAtDesc(runId, reviewerId);
        Map<UUID, String> shortIds = shortIdsOf(reviews);

        return reviews.stream()
                .map(r -> ReviewResponse.from(r, shortIds.getOrDefault(r.getEvidenceRecordId(), "?")))
                .toList();
    }

    /**
     * Ba điều kiện phải đúng, sai một cái là 400.
     *
     * <p><b>Vì sao không tin client:</b> {@code messageId} là biến thực nghiệm của user study
     * (kế hoạch §6 chia người dùng làm hai nhóm — dùng Agent và không dùng). Nhận bừa thì một
     * người chưa từng mở hội thoại vẫn đếm vào nhóm "có dùng Agent", và bảng so sánh giữa hai
     * nhóm ở tuần 16 sai mà không ai đối chiếu lại được.
     *
     * <p>Ném 400 chứ KHÔNG lặng lẽ ghi {@code NULL}: ghi NULL đẩy người có dùng Agent sang nhóm
     * đối chứng — cũng làm nhiễu, mà lại còn im lặng.
     */
    private UUID validateMessage(UUID messageId, UUID runId, UUID reviewerId) {
        if (messageId == null) {
            return null;
        }

        Message message = messageRepository
                .findById(messageId)
                .orElseThrow(() -> new InvalidReviewException("messageId khong ton tai: " + messageId));

        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new InvalidReviewException(
                    "messageId phai tro toi mot cau tra loi cua Agent, khong phai cau hoi cua nguoi dung");
        }

        Conversation conversation = conversationRepository
                .findByIdAndOwnerId(message.getConversationId(), reviewerId)
                .orElseThrow(() -> new InvalidReviewException(
                        "messageId khong thuoc hoi thoai nao cua nguoi dang xet canh bao nay"));

        if (!conversation.getAnalysisRunId().equals(runId)) {
            throw new InvalidReviewException("messageId thuoc mot lan phan tich khac: " + conversation.getAnalysisRunId());
        }

        return messageId;
    }

    /** Một truy vấn cho cả danh sách, không tra từng dòng trong vòng lặp. */
    private Map<UUID, String> shortIdsOf(List<HumanReview> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = reviews.stream().map(HumanReview::getEvidenceRecordId).toList();
        return evidenceRecordRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(EvidenceRecord::getId, EvidenceRecord::getShortId, (a, b) -> a));
    }

    /** Dùng cho export: tra nhanh quyết định theo evidence id. */
    @Transactional(readOnly = true)
    public Map<UUID, HumanReview> byEvidenceId(UUID runId, UUID reviewerId) {
        return humanReviewRepository.findByAnalysisRunIdAndReviewerIdOrderByUpdatedAtDesc(runId, reviewerId).stream()
                .collect(Collectors.toMap(HumanReview::getEvidenceRecordId, Function.identity(), (a, b) -> a));
    }
}
