package vn.hust.datn.evidence.service;

import java.util.List;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.domain.SnapshotSourceType;

/**
 * Điểm mở rộng cho các nguồn snapshot khác (vd {@code GIT_CLONE}, tuần sau) — đúng một
 * implementation hiện tại ({@link ZipUploadIngestor}), không có factory/registry vì chỉ một
 * implementation không cần một cơ chế chọn lựa.
 *
 * <p>{@code validateFormat} tách khỏi {@code extractEntries} có chủ đích: gọi trước khi
 * {@code SnapshotService} tạo hàng {@code PENDING} — kiểm rẻ, từ chối sớm input rõ ràng sai định
 * dạng (vd người dùng lỡ tải nhầm ảnh) mà không để lại bia mộ trong DB cho mọi lần thử sai.
 */
public interface SnapshotSourceIngestor {

    SnapshotSourceType supportedType();

    void validateFormat(byte[] rawContent);

    List<ExtractedEntry> extractEntries(byte[] rawContent, UploadProperties limits);
}
