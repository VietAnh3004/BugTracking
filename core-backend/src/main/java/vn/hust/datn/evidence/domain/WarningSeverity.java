package vn.hust.datn.evidence.domain;

/**
 * Thang chuẩn hoá dùng chung, khớp CHECK constraint warning.severity_normalized.
 *
 * <p>Bắt buộc phải chuẩn hoá vì SpotBugs (priority 1-3 + rank 1-20) và Semgrep (level
 * error/warning/note + precision) dùng hai thang rời rạc không giao nhau. Thang gốc của từng
 * tool vẫn giữ nguyên trong warning.severity_raw và evidence_record.content.
 */
public enum WarningSeverity {
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
