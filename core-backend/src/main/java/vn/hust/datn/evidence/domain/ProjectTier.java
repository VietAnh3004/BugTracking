package vn.hust.datn.evidence.domain;

/**
 * Hạng của một dự án — quyết định hội thoại trong dự án được dùng model nào. Chỉ admin đổi.
 */
public enum ProjectTier {
    /** Mặc định: chỉ model miễn phí. */
    FREE,
    /** Được chọn model Premium (Claude Sonnet) cho từng câu hỏi. */
    PREMIUM
}
