package vn.hust.datn.evidence.dto;

import java.util.List;

import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Metadata riêng của một cảnh báo tĩnh, có mặt khi neo thuộc loại {@code STATIC_WARNING}.
 *
 * <p>Khác {@link WarningView} (dùng cho BẢNG danh sách) ở hai trường mà một hàng bảng không chứa
 * nổi: {@code cweIds} và {@code locations}. Javadoc của {@code WarningView} đã dự liệu đúng chỗ
 * này — "endpoint detail cần nó thì query riêng".
 *
 * <p>{@code locations} là danh sách vì 4/29 cảnh báo thật của vulnshop có nhiều hơn một vị trí,
 * một cái trỏ tới ba file khác nhau. Chỉ hiện vị trí đầu là giấu bằng chứng.
 */
public record WarningDetailView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WarningTool tool,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ruleId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ruleIdNormalized,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String severityRaw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WarningSeverity severityNormalized,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "false khi tool không tự đánh dấu vị trí chính và parser phải tự chọn")
                boolean hasPrimaryLocation,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<Integer> cweIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<WarningLocationView> locations) {}
