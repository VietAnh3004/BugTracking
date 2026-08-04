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
    NOT_APPLICABLE
}
