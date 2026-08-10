package vn.hust.datn.evidence.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import vn.hust.datn.evidence.domain.ReviewDecision;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quyết định của người dùng về một cảnh báo.
 *
 * @param decision bốn lựa chọn; {@code UNCERTAIN} và {@code INSUFFICIENT_EVIDENCE} là hai thứ khác
 *     nhau, xem {@link ReviewDecision}
 * @param messageId câu trả lời của Agent người này đã đọc trước khi quyết. Bỏ trống nghĩa là không
 *     dùng Agent. Backend KIỂM giá trị này chứ không tin — nó là biến thực nghiệm của user study
 * @param secondsSpent client tự đo nên chỉ tham khảo; bỏ trống là hợp lệ và tốt hơn bịa một số
 */
@Schema(description = "Quyết định của người dùng về một cảnh báo")
public record SubmitReviewRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ReviewDecision decision,
        @Schema(description = "Câu trả lời Agent đã đọc trước khi quyết; bỏ trống = không dùng Agent")
                UUID messageId,
        @Schema(description = "Ghi chú của người xét") @Size(max = 2000) String note,
        @Schema(description = "Thời gian xét, giây. Client tự đo nên chỉ tham khảo") @Positive Integer secondsSpent) {}
