package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import vn.hust.datn.evidence.domain.GroundTruthLabel;
import vn.hust.datn.evidence.domain.GroundTruthSource;
import vn.hust.datn.evidence.domain.GroundTruthVerdict;
import vn.hust.datn.evidence.domain.ProjectRole;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bảng gán nhãn ground truth của một lần phân tích, NHÌN TỪ PHÍA NGƯỜI GỌI.
 *
 * <p>Chủ dự án thấy nhãn MANUAL của mình kèm gợi ý SEEDED; thành viên thấy nhãn
 * BLIND_SECOND_RATER của mình và KHÔNG có gợi ý nào. Lọc ở server chứ không ở giao diện: tính
 * "mù" của người gán thứ hai là điều kiện để Cohen's kappa có nghĩa, nên không được phụ thuộc vào
 * việc họ có tò mò mở DevTools hay không.
 */
@Schema(description = "Bảng gán nhãn ground truth, đã lọc theo vai trò người gọi")
public record GroundTruthSheetResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectRole role,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Nguồn nhãn người gọi đang gán: MANUAL (chủ) hoặc BLIND_SECOND_RATER (thành viên)")
                GroundTruthSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GroundTruthRow> rows) {

    /** Một cảnh báo cần gán nhãn. */
    public record GroundTruthRow(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shortId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WarningTool tool,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ruleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WarningSeverity severity,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String filePath,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer startLine,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
            @Schema(description = "Nhãn người gọi đã gán; vắng khi chưa gán") GroundTruthLabelView label,
            @Schema(description = "Gợi ý từ nhãn SEEDED — chỉ chủ dự án nhận được") GroundTruthLabelView seededHint) {}

    /** Một nhãn: loại, lý do, ai gán, lúc nào. */
    public record GroundTruthLabelView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) GroundTruthVerdict label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rationale,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String labeler,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime labeledAt) {

        public static GroundTruthLabelView from(GroundTruthLabel label) {
            return new GroundTruthLabelView(label.getLabel(), label.getRationale(), label.getLabeler(), label.getLabeledAt());
        }
    }
}
