package vn.hust.datn.evidence.domain;

/**
 * Artifact của một {@link AnalysisRun} từ đâu ra.
 *
 * <p>Hai giá trị tồn tại SONG SONG chứ không thay thế nhau: nạp thủ công là đường demo được ngay
 * bằng {@code docs/samples/}, và vẫn là đường dự phòng khi máy chủ không có Docker daemon
 * ({@code app.analysis.runner=disabled}). Không phân biệt được hai nguồn thì hai lần chạy cho ra
 * cùng số liệu nhưng khác hẳn nhau về mức độ đáng tin — mà không ai biết.
 */
public enum ArtifactSource {
    /** Người dùng tự chạy analyzer rồi tải artifact lên qua API. */
    MANUAL_UPLOAD,
    /** core-backend tự chạy analyzer trong container (ADR 0008). */
    DOCKER_SANDBOX
}
