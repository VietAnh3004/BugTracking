package vn.hust.datn.evidence.domain;

/** Loại mục trong cây file của snapshot. Khớp CHECK constraint snapshot_file.entry_type. */
public enum SnapshotEntryType {
    FILE,
    DIRECTORY
}
