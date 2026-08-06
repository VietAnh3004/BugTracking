package vn.hust.datn.evidence.dto;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kết quả một lần tính lại liên kết.
 *
 * <p>Trả cả {@code deletedCount} lẫn {@code linkCount} để người vận hành thấy được thao tác đã
 * làm gì: "xoá 123, ghi 123" và "xoá 123, ghi 45" là hai tình huống rất khác nhau, mà chỉ một con
 * số thì không phân biệt được.
 */
public record LinkRebuildResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Số liên kết cũ đã xoá trước khi ghi lại")
                int deletedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int linkCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Số cảnh báo không nhận được liên kết nào")
                int unlinkedWarningCount,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Phân bố theo EvidenceLinkMethod — nguồn số liệu tỉ lệ liên kết yếu")
                Map<String, Object> countByMethod) {}
