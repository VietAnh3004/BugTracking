package vn.hust.datn.evidence.domain;

/** Trạng thái vòng đời của một evidence_record. Khớp CHECK constraint evidence_record.status. */
public enum EvidenceStatus {
    ACTIVE,
    SUPERSEDED
}
