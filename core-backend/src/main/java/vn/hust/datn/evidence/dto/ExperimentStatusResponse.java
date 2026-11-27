package vn.hust.datn.evidence.dto;

import java.util.List;
import java.util.UUID;

import vn.hust.datn.evidence.domain.ExperimentConfig;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tiến độ và kết quả của thực nghiệm 3 cấu hình trên một lần phân tích ({@code docs/thuc-nghiem.md}).
 *
 * <p>Như {@link RunMetricsResponse}: mọi tỉ lệ là {@code Double} bọc, {@code null} = mẫu số bằng 0
 * (chưa đủ dữ liệu), giao diện hiện "—" chứ không phải {@code 0%}.
 */
@Schema(description = "Tiến độ và chỉ số của thực nghiệm 3 cấu hình")
public record ExperimentStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID analysisRunId,
        @Schema(description = "Đang có một lượt chạy trên máy chủ", requiredMode = Schema.RequiredMode.REQUIRED)
                boolean running,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int repeats,
        @Schema(description = "Số cảnh báo × 3 cấu hình × số lần lặp", requiredMode = Schema.RequiredMode.REQUIRED)
                long totalCells,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long succeededCells,
        @Schema(description = "Ô hỏng, sẽ được chạy lại khi bấm Chạy tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
                long failedCells,
        @Schema(description = "Ô đang chạy, dạng EV-0123 · LLM_CODE · lần 2") String currentCell,
        @Schema(description = "Vì sao lượt chạy gần nhất dừng; vắng khi chưa dừng lần nào") String lastStopReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ConfigMetrics> configs) {

    /**
     * Chỉ số của MỘT cấu hình. Độ chính xác so với nhãn ground truth MANUAL; {@code INSUFFICIENT_EVIDENCE}
     * tính là sai (giống {@code agentAccuracy} của dashboard), còn {@code decidedAccuracy} bỏ các ô đó ra.
     */
    @Schema(description = "Chỉ số của một cấu hình")
    public record ConfigMetrics(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExperimentConfig config,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long succeededCells,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long failedCells,
            @Schema(description = "Gộp mọi lần lặp; vắng khi chưa có ô nào có nhãn") Double accuracy,
            @Schema(description = "Độ lệch chuẩn của độ chính xác giữa các lần lặp; cần ít nhất 2 lần lặp có dữ liệu")
                    Double accuracyStdDev,
            @Schema(description = "Độ chính xác của từng lần lặp, theo thứ tự; null = lần đó chưa có dữ liệu",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    List<Double> accuracyByRepeat,
            @Schema(description = "Độ chính xác trên các ô có kết luận (bỏ INSUFFICIENT_EVIDENCE)")
                    Double decidedAccuracy,
            @Schema(description = "Tỉ lệ ô trả INSUFFICIENT_EVIDENCE") Double insufficientRate,
            @Schema(description = "Tỉ lệ nhận định không có trích dẫn hợp lệ") Double unsupportedClaimRate,
            @Schema(description = "Tỉ lệ trích dẫn tra được về bằng chứng thật") Double citationResolvedRate,
            @Schema(description = "Tỉ lệ cảnh báo cho cùng verdict ở mọi lần lặp (chỉ tính cảnh báo đã đủ lần lặp)")
                    Double stability,
            @Schema(description = "Trung bình số lượt gọi model mỗi ô") Double avgLlmCalls,
            @Schema(description = "Trung bình số tool call mỗi ô") Double avgToolCalls,
            @Schema(description = "Trung bình token vào mỗi ô") Double avgInputTokens,
            @Schema(description = "Trung bình token ra mỗi ô") Double avgOutputTokens,
            @Schema(description = "Trung bình độ trễ mỗi ô, mili giây") Double avgLatencyMs) {}
}
