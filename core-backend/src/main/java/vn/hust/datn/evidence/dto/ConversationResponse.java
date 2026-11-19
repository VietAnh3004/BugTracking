package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.Conversation;
import vn.hust.datn.evidence.domain.ProjectTier;

import io.swagger.v3.oas.annotations.media.Schema;

/** Một phiên hỏi đáp, neo vào đúng một lần phân tích. */
@Schema(description = "Phiên hỏi đáp với Agent, gắn với một lần phân tích")
public record ConversationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID analysisRunId,
        @Schema(description = "Cảnh báo đang bàn tới (EV-XXXX); null nghĩa là hỏi ở phạm vi cả run")
                String focusShortId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Hạng của dự án chứa hội thoại; PREMIUM thì giao diện cho chọn model từng câu")
                ProjectTier projectTier) {

    public static ConversationResponse from(Conversation conversation, ProjectTier projectTier) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getAnalysisRunId(),
                conversation.getFocusShortId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                projectTier);
    }
}
