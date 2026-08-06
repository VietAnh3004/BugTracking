package vn.hust.datn.evidence.dto;

import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.domain.TestFailureKind;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tóm tắt một test case, gắn kèm {@link RelatedEvidenceView} khi bằng chứng liên quan là một test.
 *
 * <p>Tồn tại để tránh N+1 ở tầng giao diện: không có nó, Evidence Explorer phải gọi thêm một
 * request cho MỖI thẻ test chỉ để biết nó hỏng vì lý do gì.
 *
 * <p>Cố ý KHÔNG chứa mảng lồng nhau (không có danh sách stack frame): đây là thứ để vẽ một THẺ
 * TÓM TẮT, không phải để thay trang chi tiết. Muốn xem đầy đủ thì bấm vào thẻ và mở chính evidence
 * đó — ranh giới rõ ràng thì response không phình theo thời gian.
 */
public record TestCaseSummaryView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String className,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String methodName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TestCaseStatus status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) TestFailureKind failureKind,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String failureType,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String failureMessage,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer durationMs,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Evidence STACK_TRACE của chính test này; NULL khi test không hỏng")
                String stackTraceShortId) {}
