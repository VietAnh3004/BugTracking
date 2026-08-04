package vn.hust.datn.evidence.domain;

/**
 * Kiểu hỏng của một test. Khớp CHECK constraint test_case.failure_kind.
 *
 * <p>Tên hai thẻ XML của Surefire NGƯỢC trực giác nên ghi rõ ở đây: {@code <failure>} là
 * assertion thất bại, {@code <error>} là ngoại lệ thoát ra khỏi test. Phân biệt được hai thứ này
 * là điều kiện để liên kết bằng chứng đúng — chỉ kiểu ERROR mới có stack trace trỏ về mã
 * production (phát hiện 2, docs/evidence-model.md mục 6).
 */
public enum TestFailureKind {
    /** Thẻ {@code <failure>}: assertion thất bại, ném từ chính lớp test. */
    FAILURE,
    /** Thẻ {@code <error>}: ngoại lệ thoát ra, thường ném từ mã production. */
    ERROR
}
