package vn.hust.datn.evidence.domain;

/**
 * Cách một evidence_link được suy ra — khớp CHECK constraint evidence_link.method.
 *
 * <p>Tồn tại vì 3/5 test hỏng trong artifact thật (docs/samples/vulnshop/) không có stack frame
 * nào trỏ về mã production: nếu chỉ có STACK_FRAME thì 60% liên kết test-hỏng sẽ không sinh ra
 * được. Ghi lại phương pháp cho phép tuần 15 đo bao nhiêu % link chỉ dựa vào quy ước tên
 * (NAMING_CONVENTION — yếu nhất) so với bằng chứng trực tiếp.
 */
public enum EvidenceLinkMethod {
    LINE_EXACT,
    LINE_NEAREST_EXECUTABLE,
    RANGE_OVERLAP,
    METHOD_AGGREGATE,
    STACK_FRAME,
    NAMING_CONVENTION,
    SYMBOL_MATCH,
    CROSS_TOOL_AGREEMENT
}
