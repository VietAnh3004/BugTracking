package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.HumanReview;
import vn.hust.datn.evidence.domain.ReviewDecision;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một quyết định đã ghi.
 *
 * <p>{@code shortId} có mặt để giao diện khỏi phải tra ngược UUID → Evidence ID: bảng cảnh báo
 * hiển thị theo {@code shortId}, và bắt nó đối chiếu bằng UUID là tạo thêm một chỗ để sai.
 */
@Schema(description = "Quyết định của người dùng về một cảnh báo")
public record ReviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shortId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewDecision decision,
        @Schema(description = "Khác null nghĩa là người này đã đọc Agent trước khi quyết")
                UUID messageId,
        String note,
        Integer secondsSpent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt) {

    public static ReviewResponse from(HumanReview review, String shortId) {
        return new ReviewResponse(
                review.getId(),
                shortId,
                review.getDecision(),
                review.getMessageId(),
                review.getNote(),
                review.getSecondsSpent(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
