package vn.hust.datn.evidence.service.analysis.sandbox;

/**
 * Hạ tầng sandbox hỏng: không nói chuyện được với Docker daemon, container hết thời gian, không
 * copy được artifact ra.
 *
 * <p>KHÔNG dùng cho "analyzer chạy xong nhưng kết quả xấu" — đó là chuyện của bốn tầng kiểm tính
 * tỉnh táo, và chúng cần artifact trong tay để phán xét. Trộn hai loại vào một exception thì
 * người vận hành đọc {@code error_message} không phân biệt được "máy hỏng" với "mã nguồn có vấn
 * đề", mà hai thứ đó cần hai hành động khác nhau.
 */
public class SandboxException extends RuntimeException {

    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
