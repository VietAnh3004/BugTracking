package vn.hust.datn.evidence.dto;

import java.util.UUID;

import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một bằng chứng liên quan, cho Evidence Explorer.
 *
 * <p>Tên KHÔNG được trùng {@code ToolEvidenceItem} của {@code dto/tool/}: springdoc đặt tên schema
 * OpenAPI theo <b>simple name</b>, nên hai DTO trùng tên ở hai package sẽ đè nhau IM LẶNG và client
 * TypeScript sinh ra sẽ mang hình dạng của cái nào springdoc gặp sau (ADR 0005 quyết định 4).
 *
 * <p>Khác {@code ToolEvidenceItem} ở ba chỗ, và cả ba đều có lý do: camelCase (đường UI), thêm
 * {@code rationale} (câu giải thích cho người đọc — LLM không cần vì nó tự suy từ method/weight),
 * và thêm {@code direction} (hướng của liên kết, thứ quyết định cách viết câu trong giao diện).
 */
public record RelatedEvidenceView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evidenceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shortId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String filePath,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer startLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer endLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String symbol,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceLinkRelation relation,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Cách suy ra liên kết. NAMING_CONVENTION là yếu nhất — giao diện PHẢI hiển thị")
                EvidenceLinkMethod method,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Độ tin cậy của LIÊN KẾT, không phải mức độ phủ")
                double weight,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Câu giải thích duy nhất người dùng đọc được cho một liên kết")
                String rationale,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "true khi neo nằm ở đầu 'from' của liên kết")
                boolean outgoing,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Chỉ có khi type là TEST_RESULT/FAILED_TEST")
                TestCaseSummaryView testCase) {}
