package vn.hust.datn.evidence.domain;

/**
 * Khớp CHECK constraint analysis_run.status.
 *
 * <p>SUCCEEDED_CLEAN tách riêng khỏi SUCCEEDED có chủ đích: một dự án sạch thật (0 cảnh báo) và
 * một analyzer im lặng hỏng (cũng 0 cảnh báo) không được phép trông giống nhau trong dữ liệu —
 * xem ADR 0003 và ràng buộc ck_run_not_silently_empty / ck_run_clean_is_explicit ở V2 migration.
 */
public enum AnalysisRunStatus {
    RUNNING,
    SUCCEEDED,
    SUCCEEDED_CLEAN,
    FAILED
}
