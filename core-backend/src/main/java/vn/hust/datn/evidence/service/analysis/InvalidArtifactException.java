package vn.hust.datn.evidence.service.analysis;

/**
 * Artifact của analyzer không đọc được, sai định dạng, hoặc rỗng một cách đáng ngờ.
 *
 * <p>Cố ý là exception chứ không phải trả về danh sách rỗng: nguyên tắc xuyên suốt dự án là
 * "analyzer trả về 0 cảnh báo phải bị coi là lỗi". Một {@code List.of()} lặng lẽ sẽ thành một
 * {@code analysis_run} rỗng trông y hệt một repo sạch.
 */
public class InvalidArtifactException extends RuntimeException {

    public InvalidArtifactException(String message) {
        super(message);
    }

    public InvalidArtifactException(String message, Throwable cause) {
        super(message, cause);
    }
}
