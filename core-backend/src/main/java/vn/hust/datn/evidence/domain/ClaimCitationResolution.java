package vn.hust.datn.evidence.domain;

/**
 * Kết quả khi CitationValidator cố resolve một Evidence ID mà claim trích dẫn — khớp CHECK
 * constraint claim_citation.resolution.
 *
 * <p>Mẫu số của Evidence Citation Accuracy: mọi lượt trích dẫn đều lưu, kể cả khi LLM bịa ID
 * không tồn tại (UNKNOWN_ID) hay trích dẫn evidence của run khác (WRONG_RUN).
 */
public enum ClaimCitationResolution {
    RESOLVED,
    UNKNOWN_ID,
    WRONG_RUN,
    MALFORMED
}
