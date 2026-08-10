package vn.hust.datn.evidence.domain;

/**
 * Nhãn chuẩn đến từ đâu.
 *
 * <p>{@link #BLIND_SECOND_RATER} có mặt TỪ ĐẦU dù tuần 15 mới dùng: kế hoạch §5 đòi một người gán
 * nhãn độc lập trên một tập con để báo cáo được Cohen's kappa. Thêm giá trị vào CHECK constraint
 * sau sẽ phải sửa cả truy vấn thống kê lẫn migration.
 */
public enum GroundTruthSource {
    /** Lấy thẳng từ {@code case-study/vulnshop/SEEDED.md} — lỗi gieo có chủ đích. */
    SEEDED,
    /** Người gán nhãn chính, dựa trên đọc mã. */
    MANUAL,
    /** Người thứ hai gán độc lập, để tính inter-rater agreement. */
    BLIND_SECOND_RATER
}
