package vn.hust.datn.evidence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Trạng thái độ phủ của CHÍNH dòng bị cảnh báo, cho thẻ "Độ phủ" ở trang cảnh báo.
 *
 * <p>Lỗi gặp thật 23/11/2026: thẻ chỉ có câu tóm tắt CỦA CẢ FILE ("13/20 dòng được phủ") ở chỗ nổi
 * nhất, còn trạng thái của dòng cảnh báo nằm trong câu kỹ thuật {@code Dòng 72: MISSED (ci=0 mi=9 …)}.
 * Người đọc tưởng dòng đã được test trong khi không test nào chạy qua. Trường có cấu trúc này để giao
 * diện nói thẳng điều đó, không phải tự tách chữ trong {@code rationale}.
 */
@Schema(description = "Độ phủ của chính dòng bị cảnh báo")
public record LineCoverageView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Dòng mà số liệu dưới đây thuộc về")
                int line,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"COVERED", "PARTIAL", "MISSED"})
                String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ci: số lệnh bytecode được chạy")
                int coveredInstructions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "mi: số lệnh bytecode bị bỏ qua")
                int missedInstructions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "cb: số nhánh được chạy")
                int coveredBranches,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "mb: số nhánh bị bỏ qua")
                int missedBranches,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "false khi dòng cảnh báo không thực thi được và đây là dòng gần nhất")
                boolean exactLine) {}
