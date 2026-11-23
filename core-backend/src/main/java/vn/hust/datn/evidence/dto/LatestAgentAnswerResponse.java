package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.ClaimVerdict;
import vn.hust.datn.evidence.domain.Message;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Câu trả lời Agent mới nhất mà CHÍNH người gọi đã nhận về một cảnh báo.
 *
 * <p>Ô đánh giá ở trang cảnh báo dùng nó để gắn {@code messageId} vào quyết định: người dùng hỏi Agent
 * ở trang chat TRƯỚC rồi mới sang trang cảnh báo đánh giá, nên trang cảnh báo không tự biết câu trả
 * lời nào đã được đọc. Mọi trường vắng mặt nghĩa là người này chưa hỏi Agent về cảnh báo đó.
 */
@Schema(description = "Câu trả lời Agent mới nhất của người gọi cho một cảnh báo; mọi trường vắng mặt = chưa hỏi")
public record LatestAgentAnswerResponse(
        UUID messageId, OffsetDateTime answeredAt, String model, ClaimVerdict verdict) {

    public static final LatestAgentAnswerResponse NONE = new LatestAgentAnswerResponse(null, null, null, null);

    public static LatestAgentAnswerResponse from(Message message) {
        return new LatestAgentAnswerResponse(
                message.getId(), message.getCreatedAt(), message.getModel(), message.getVerdict());
    }
}
