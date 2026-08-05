package vn.hust.datn.evidence.exception;

/**
 * Sandbox không dùng được lúc này: {@code app.sandbox.runner=disabled}, hoặc Docker daemon không
 * trả lời.
 *
 * <p>503 chứ không phải 500: đây là trạng thái tạm thời của hạ tầng, không phải lỗi lập trình. Và
 * ném TRƯỚC khi tạo hàng {@code analysis_run} — không để lại bia mộ cho một việc còn chưa bắt đầu
 * được.
 */
public class SandboxUnavailableException extends RuntimeException {

    public SandboxUnavailableException(String message) {
        super(message);
    }
}
