package vn.hust.datn.evidence.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ClaimVerdict;
import vn.hust.datn.evidence.domain.MessageRole;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một lượt trong hội thoại, ĐÃ qua hậu kiểm trích dẫn.
 *
 * <p>Tên là {@code ChatMessageResponse} chứ không phải {@code MessageResponse}: springdoc đặt tên
 * schema OpenAPI theo SIMPLE NAME, nên một DTO trùng tên ở package khác sẽ đè lên nhau trong im
 * lặng (ADR 0005 quyết định 4).
 *
 * <p>{@code downgradedFromVerdict} khác null nghĩa là CitationValidator đã hạ cấp lượt này. Giá
 * trị đó ra tới giao diện có chủ đích — giấu đi thì người dùng thấy một {@code INSUFFICIENT_EVIDENCE}
 * không rõ vì mô hình thận trọng hay vì nó bịa trích dẫn, mà hai điều đó khác hẳn nhau.
 */
@Schema(description = "Một lượt hỏi hoặc đáp, đã qua hậu kiểm trích dẫn")
public record ChatMessageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ordinal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MessageRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(description = "Chỉ có ở lượt của Agent") ClaimVerdict verdict,
        @Schema(description = "Verdict gốc trước khi bị hạ cấp vì thiếu trích dẫn hợp lệ")
                ClaimVerdict downgradedFromVerdict,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> suggestedNextChecks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> contradictingShortIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClaimView> claims,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt) {

    /** Một nhận định của Agent kèm kết quả tra từng trích dẫn. */
    @Schema(description = "Một nhận định kèm kết quả hậu kiểm trích dẫn")
    public record ClaimView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ordinal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClaimVerdict verdict,
            @Schema(description = "Khác null nghĩa là nhận định này không có trích dẫn nào tra được")
                    ClaimVerdict downgradedFromVerdict,
            BigDecimal confidence,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CitationView> citations) {}

    /**
     * Một lượt trích dẫn đã tra xong.
     *
     * <p>{@code citedShortIdRaw} là NGUYÊN VĂN mô hình viết, kể cả khi vô nghĩa. Giao diện hiện
     * cả những cái hỏng — chúng là mẫu số của Evidence Citation Accuracy, và giấu đi thì người
     * chấm không thấy được tỉ lệ thật.
     */
    @Schema(description = "Một Evidence ID được trích dẫn, kèm kết quả tra")
    public record CitationView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String citedShortIdRaw,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClaimCitationResolution resolution,
            @Schema(description = "Chỉ khác null khi resolution=RESOLVED; dùng để mở Evidence Explorer")
                    String shortId) {}
}
