package vn.hust.datn.evidence.exception;

/**
 * Yêu cầu đọc nội dung theo dòng của một file nhị phân. 415 Unsupported Media Type.
 *
 * <p>Không trả về nội dung đã "ép" thành UTF-8: byte nhị phân giải mã sang chuỗi sẽ sinh ký tự
 * thay thế (U+FFFD) không khôi phục được, và số dòng đếm trên đó vô nghĩa — source viewer hiển thị
 * rác trông như dữ liệu thật.
 */
public class BinaryFileException extends RuntimeException {

    public BinaryFileException(String path) {
        super("File '%s' là nhị phân, không đọc được theo dòng".formatted(path));
    }
}
