package vn.hust.datn.evidence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một vị trí của cảnh báo.
 *
 * <p>Giữ CẢ {@code pathRaw} lẫn {@code filePath}: cái đầu là nguyên văn analyzer khai, cái sau là
 * bản đã đối chiếu với snapshot. Khi {@code filePath} NULL mà {@code pathRaw} có giá trị, giao diện
 * nói được "analyzer khai đường dẫn này nhưng không tìm thấy trong mã nguồn đã tải lên" — một câu
 * cụ thể, thay vì im lặng bỏ vị trí đó đi.
 */
public record WarningLocationView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ordinal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean primary,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String roleRaw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nguyên văn analyzer khai")
                String pathRaw,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Đã đối chiếu với snapshot; NULL nghĩa là không tìm thấy trong mã nguồn đã tải lên")
                String filePath,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer startLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer endLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String className,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String methodName) {}
