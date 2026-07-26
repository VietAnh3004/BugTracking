package vn.hust.datn.evidence.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Điểm xử lý lỗi duy nhất của toàn bộ API.
 *
 * <p>Controller không được bắt lỗi rải rác: mọi ngoại lệ đều nổi lên đây và trả về cùng một định
 * dạng RFC 7807 (Problem Details), nhờ đó frontend chỉ cần một chỗ để hiển thị lỗi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://datn.hust.vn/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Không tìm thấy tài nguyên", ex.getMessage(), request);
    }

    /** Lỗi validate đầu vào: gom tất cả field sai vào một response thay vì báo từng cái một. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "giá trị không hợp lệ" : error.getDefaultMessage(),
                        (first, second) -> first));

        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST, "validation-failed", "Dữ liệu đầu vào không hợp lệ", null, request);
        detail.setProperty("fieldErrors", fieldErrors);
        return detail;
    }

    /**
     * Lưới an toàn cuối cùng.
     *
     * <p>Thông điệp gốc được ghi log nhưng không trả về cho client — nó có thể lộ chi tiết nội bộ
     * như cấu trúc bảng hay đường dẫn file trên server.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Lỗi hệ thống",
                "Đã xảy ra lỗi không mong đợi. Vui lòng thử lại hoặc liên hệ quản trị viên.",
                request);
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail, HttpServletRequest req) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        if (detail != null) {
            problem.setDetail(detail);
        }
        problem.setInstance(URI.create(req.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
