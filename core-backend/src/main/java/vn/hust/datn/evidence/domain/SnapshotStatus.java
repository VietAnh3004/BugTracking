package vn.hust.datn.evidence.domain;

/** Trạng thái vòng đời của một repository_snapshot. Khớp CHECK constraint repository_snapshot.status. */
public enum SnapshotStatus {
    PENDING,
    EXTRACTING,
    READY,
    FAILED
}
