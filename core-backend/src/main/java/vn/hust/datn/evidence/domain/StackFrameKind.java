package vn.hust.datn.evidence.domain;

/**
 * Phân loại một frame trong stack trace. Khớp CHECK constraint stack_frame.frame_kind.
 *
 * <p>{@link #UNKNOWN} là giá trị THÊM so với thiết kế ban đầu ở docs/evidence-model.md mục 7:
 * một dòng {@code at ...} không parse được vẫn phải sinh ra hàng. Bỏ qua im lặng là đúng thứ dự
 * án này chống — nếu regex tách frame sai, ta muốn thấy một đống UNKNOWN chứ không phải một
 * stack trace ngắn đi một cách vô hại.
 */
public enum StackFrameKind {
    /** Mã của chính dự án đang phân tích, không phải lớp test. */
    PRODUCTION,
    /** Lớp test của dự án đang phân tích. */
    TEST,
    /** JUnit, AssertJ, Mockito… */
    TEST_FRAMEWORK,
    /** Lớp thuộc JDK (thường có tiền tố module {@code java.base/}). */
    JDK,
    /** Thư viện bên thứ ba khác. */
    LIBRARY,
    /** Dòng {@code at ...} không parse được — giữ lại raw_line để reparse. */
    UNKNOWN
}
