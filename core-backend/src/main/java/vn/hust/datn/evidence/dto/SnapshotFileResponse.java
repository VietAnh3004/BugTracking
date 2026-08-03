package vn.hust.datn.evidence.dto;

import vn.hust.datn.evidence.domain.SnapshotEntryType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một mục trong danh sách PHẲNG của {@code GET /snapshots/{id}/files} — FE tự dựng cây từ
 * {@code path} (xem kế hoạch tuần 5-7: repo cỡ đồ án vài trăm file, JSON phẳng nhỏ hơn cây lồng và
 * cho phép FE giữ state thư mục đang mở khi đổi file).
 *
 * <p>Không lộ {@code storageKey}/{@code sha256}/{@code id}: key object storage là chi tiết hạ tầng,
 * FE đọc nội dung qua {@code /snapshots/{id}/file?path=} chứ không bao giờ chạm MinIO trực tiếp.
 */
public record SnapshotFileResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String parentPath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SnapshotEntryType entryType,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String extension,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "null cho thư mục và file nhị phân — 'không đếm được', khác với '0 dòng'")
                Integer lineCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean binary,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "false khi file vượt ngưỡng lưu trữ: vẫn hiện trong cây nhưng không đọc được nội dung")
                boolean stored) {}
