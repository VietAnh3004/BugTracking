package vn.hust.datn.evidence.exception;

/**
 * Cây mã nguồn đã upload không phân tích được bằng sandbox: không có {@code pom.xml}, không có
 * file {@code .java}, nhiều module, hoặc thiếu nội dung file nguồn.
 *
 * <p>Đây là 409 chứ KHÔNG phải một {@code analysis_run} với 0 cảnh báo. Phân biệt hai thứ này là
 * điểm mấu chốt: một repo Gradle chạy qua sandbox Maven sẽ cho ra 0 class, 0 cảnh báo, exit 0 —
 * trông y hệt một dự án sạch. Từ chối tường minh kèm lý do đọc được là cách duy nhất để người
 * dùng biết phải làm gì.
 */
public class UnsupportedSourceTreeException extends RuntimeException {

    public UnsupportedSourceTreeException(String message) {
        super(message);
    }
}
