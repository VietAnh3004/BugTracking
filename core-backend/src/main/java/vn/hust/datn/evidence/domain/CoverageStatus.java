package vn.hust.datn.evidence.domain;

/**
 * Trạng thái phủ của một dòng. Khớp CHECK constraint coverage_line.status.
 *
 * <p>Cột trong DB là GENERATED từ mi/ci/mb/cb nên enum này chỉ dùng cho đường ĐỌC.
 *
 * <p>Lưu ý dễ sai: "dòng được phủ" theo định nghĩa của JaCoCo là {@code ci > 0}, tức
 * {@link #COVERED} <b>cộng</b> {@link #PARTIAL} (77 + 5 = 82 trên vulnshop, khớp counter của
 * chính JaCoCo). Đếm mỗi COVERED ra 77 là sai.
 */
public enum CoverageStatus {
    /** Mọi instruction và nhánh của dòng đều đã chạy. */
    COVERED,
    /** Dòng có chạy nhưng còn instruction hoặc nhánh chưa chạy. */
    PARTIAL,
    /** Không instruction nào của dòng được chạy. */
    MISSED
}
