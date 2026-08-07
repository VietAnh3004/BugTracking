package vn.hust.datn.evidence.exception;

/**
 * agent-service không gọi được, hoặc trả về thứ không đọc nổi.
 *
 * <p>Đây là lỗi HẠ TẦNG chứ không phải lỗi của người dùng, nên nó ra 503 kèm lý do đọc được —
 * cùng khuôn với sandbox tắt ở {@link vn.hust.datn.evidence.service.analysis.sandbox}. Trả 500
 * kèm stack trace sẽ khiến người vận hành đi tìm bug trong core-backend trong khi thứ chết là
 * một service khác.
 */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }
}
