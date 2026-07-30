package vn.hust.datn.evidence.domain;

/** Công cụ sinh ra evidence_record. Khớp CHECK constraint của evidence_record.source. */
public enum EvidenceSource {
    spotbugs,
    semgrep,
    surefire,
    jacoco,
    parser
}
