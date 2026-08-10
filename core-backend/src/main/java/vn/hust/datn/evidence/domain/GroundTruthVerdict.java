package vn.hust.datn.evidence.domain;

/**
 * Nhãn chuẩn của một cảnh báo, dùng làm mốc so sánh cho verdict của Agent ở tuần 15.
 *
 * <p>{@link #UNCERTAIN} có mặt vì kế hoạch §5 nói rõ nhãn là {@code TP/FP/UNCERTAIN}. Ép người
 * gán nhãn phải chọn TP hoặc FP cho một ca thật sự mơ hồ là tự tạo nhiễu cho chính mình.
 */
public enum GroundTruthVerdict {
    TRUE_POSITIVE,
    FALSE_POSITIVE,
    UNCERTAIN
}
