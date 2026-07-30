package vn.hust.datn.evidence.domain;

/**
 * Nguồn mã nguồn của một snapshot. Khớp CHECK constraint repository_snapshot.source_type.
 *
 * <p>{@code GIT_CLONE} chưa hiện thực ở tuần 5-7 — cột và giá trị có sẵn để tuần sau cắm
 * {@code SnapshotSourceIngestor} tương ứng mà không phải migration lại.
 */
public enum SnapshotSourceType {
    ZIP_UPLOAD,
    GIT_CLONE
}
