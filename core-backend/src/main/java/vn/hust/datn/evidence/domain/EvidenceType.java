package vn.hust.datn.evidence.domain;

/** 6 loại bằng chứng theo mục 3.3 đề cương. Khớp CHECK constraint của evidence_record.type. */
public enum EvidenceType {
    STATIC_WARNING,
    CODE_CONTEXT,
    TEST_RESULT,
    FAILED_TEST,
    STACK_TRACE,
    COVERAGE_INFORMATION
}
