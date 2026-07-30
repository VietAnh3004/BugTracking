package vn.hust.datn.evidence.domain;

/**
 * 3 loại verdict của Agent — khớp CHECK constraint claim.verdict và claim.downgraded_from_verdict.
 *
 * <p>Claim không gắn được Evidence ID hợp lệ bị CitationValidator hạ xuống
 * INSUFFICIENT_EVIDENCE, KHÔNG bao giờ xoá — cần giữ để tính Unsupported Claim Rate.
 */
public enum ClaimVerdict {
    LIKELY_TRUE_POSITIVE,
    LIKELY_FALSE_POSITIVE,
    INSUFFICIENT_EVIDENCE
}
