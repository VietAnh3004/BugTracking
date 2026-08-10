package vn.hust.datn.evidence.domain;

/**
 * Quyết định của người dùng về một cảnh báo.
 *
 * <p>Bốn lựa chọn theo đúng lộ trình tuần 14, và khung lý thuyết là Human-in-the-loop
 * ({@code docs/related-work.md} mục 5-C).
 *
 * <p>{@link #UNCERTAIN} và {@link #INSUFFICIENT_EVIDENCE} là HAI thứ khác nhau, cố ý không gộp:
 * cái đầu là "tôi đã xem đủ bằng chứng và vẫn không quyết được", cái sau là "hệ thống chưa cho
 * tôi đủ bằng chứng để xem". Gộp lại thì mất đúng thông tin cho biết nên cải thiện Agent hay cải
 * thiện pipeline bằng chứng.
 */
public enum ReviewDecision {
    /** Cảnh báo đúng, nên sửa mã. */
    ACCEPT,
    /** Cảnh báo báo nhầm. */
    REJECT,
    /** Đã xem đủ nhưng vẫn không quyết được. */
    UNCERTAIN,
    /** Bằng chứng hệ thống đưa ra chưa đủ để phán xét. */
    INSUFFICIENT_EVIDENCE
}
