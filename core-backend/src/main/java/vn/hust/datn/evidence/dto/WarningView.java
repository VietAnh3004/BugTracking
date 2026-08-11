package vn.hust.datn.evidence.dto;

import java.util.UUID;

import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Read model phẳng cho {@code warning} — sinh bằng JPQL constructor projection, KHÔNG bằng
 * MapStruct từ entity {@code Warning}.
 *
 * <p>Lý do: {@code Warning.evidenceRecord} là {@code @OneToOne LAZY} một chiều; map từ entity sẽ
 * hoặc ném {@code LazyInitializationException} ngoài transaction, hoặc gây N+1 query mỗi item
 * trong list. Constructor projection là một query duy nhất, không lazy loading — xem
 * {@code WarningRepository.findFlatByRunAndShortId}.
 *
 * <p>{@code cweIds} cố tình KHÔNG có ở đây: kiểu {@code Integer[]} không đưa vào JPQL constructor
 * expression sạch được. Endpoint detail cần nó thì query riêng.
 */
public record WarningView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evidenceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shortId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID analysisRunId,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Vắng khi không đối chiếu được với snapshot — cảnh báo vẫn hợp lệ, chỉ là không bấm được")
                String filePath,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer startLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer endLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String symbol,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WarningTool tool,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ruleId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ruleIdNormalized,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String severityRaw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WarningSeverity severityNormalized,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "false khi tool không tự đánh dấu vị trí chính và parser phải tự chọn")
                boolean hasPrimaryLocation) {}
