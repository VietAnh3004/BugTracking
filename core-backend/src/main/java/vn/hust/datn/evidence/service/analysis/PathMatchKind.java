package vn.hust.datn.evidence.service.analysis;

/**
 * Mức độ tin cậy của việc đối chiếu một đường dẫn do analyzer khai với chỉ mục
 * {@code snapshot_file.path}.
 *
 * <p>Cố tình KHÔNG lên DB thành cột riêng: sẽ phải thêm một CHECK + một method
 * {@code EnumCheckConstraintTest} cho thứ chưa có consumer nào. Giá trị nằm trong
 * {@code evidence_record.content.path_match_kind}, đủ để tuần 15 tính "bao nhiêu phần trăm vị trí
 * chỉ khớp được bằng tên file".
 */
public enum PathMatchKind {
    /** Đường dẫn sau chuẩn hoá thô trùng khít một entry của snapshot. */
    EXACT,
    /** Khớp bằng hậu tố dài nhất và duy nhất — trường hợp thường gặp nhất trong thực tế. */
    SUFFIX,
    /** Chỉ khớp được bằng tên file trần, và tên đó duy nhất trong repo. Độ tin cậy thấp nhất. */
    FILENAME_UNIQUE,
    /** Có nhiều hơn một ứng viên — KHÔNG đoán bừa, để {@code filePath} NULL. */
    AMBIGUOUS,
    /** Không có ứng viên nào. */
    NOT_FOUND,
    /**
     * Đường dẫn này vốn KHÔNG thuộc snapshot nên không được tính là thất bại — vd frame stack
     * trace của JDK/JUnit/thư viện. Nếu tính, tỉ lệ thất bại của một run hoàn toàn đúng vẫn là
     * ~84% (37/44 frame của vulnshop) và bộ đếm mất sạch giá trị cảnh báo.
     */
    NOT_APPLICABLE,
    /**
     * Không có phép đối chiếu nào diễn ra vì đường dẫn LẤY THẲNG từ {@code snapshot_file.path} —
     * trường hợp của {@code CODE_CONTEXT}, thứ ta tự sinh chứ không do analyzer khai.
     *
     * <p>Khác {@link #EXACT} ở chỗ EXACT là một phép khớp ĐÃ THÀNH CÔNG (có thể thất bại), còn đây
     * là không có gì để khớp. Khác {@link #NOT_APPLICABLE} ở chỗ {@code filePath} vẫn có giá trị.
     * Không tính vào {@code location_count}: cộng những đường dẫn chắc chắn đúng vào mẫu số sẽ
     * PHA LOÃNG tỉ lệ thất bại và làm hỏng chính chỉ số sức khoẻ của chuẩn hoá đường dẫn.
     */
    SELF
}
