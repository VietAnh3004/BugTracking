package vn.hust.datn.evidence.exception;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Điểm xử lý lỗi duy nhất của toàn bộ API.
 *
 * <p>Controller không được bắt lỗi rải rác: mọi ngoại lệ đều nổi lên đây và trả về cùng một định
 * dạng RFC 7807 (Problem Details), nhờ đó frontend chỉ cần một chỗ để hiển thị lỗi.
 *
 * <p>Chỉ bắt được ngoại lệ trong {@code DispatcherServlet} — 401/403 của Spring Security phát sinh
 * ở tầng servlet filter, TRƯỚC {@code DispatcherServlet}, nên có handler riêng
 * ({@code vn.hust.datn.evidence.config.security.ProblemDetailAuthenticationEntryPoint}/{@code
 * ProblemDetailAccessDeniedHandler}) dùng chung {@link ProblemDetails} với lớp này.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Không tìm thấy tài nguyên", ex.getMessage(), request);
    }

    /** short_id không tồn tại ở đâu cả — tương ứng ClaimCitationResolution.UNKNOWN_ID. */
    @ExceptionHandler(EvidenceNotFoundException.class)
    public ProblemDetail handleEvidenceNotFound(EvidenceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "evidence-not-found", "Không tìm thấy evidence", ex.getMessage(), request);
    }

    /** short_id tồn tại nhưng ở run khác — tương ứng ClaimCitationResolution.WRONG_RUN. */
    @ExceptionHandler(EvidenceWrongRunException.class)
    public ProblemDetail handleEvidenceWrongRun(EvidenceWrongRunException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND, "evidence-wrong-run", "Evidence không thuộc run này", ex.getMessage(), request);
    }

    /** get_warning gọi lên một evidence không phải STATIC_WARNING — lỗi đầu vào, không phải 404. */
    @ExceptionHandler(EvidenceNotAWarningException.class)
    public ProblemDetail handleEvidenceNotAWarning(EvidenceNotAWarningException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST, "evidence-not-a-warning", "Evidence không phải cảnh báo tĩnh", ex.getMessage(), request);
    }

    /** Run đang RUNNING/FAILED — kết quả rỗng lúc này vô nghĩa, phân biệt với "rỗng vì đúng là rỗng". */
    @ExceptionHandler(RunNotQueryableException.class)
    public ProblemDetail handleRunNotQueryable(RunNotQueryableException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "run-not-queryable", "Run chưa thể truy vấn", ex.getMessage(), request);
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
     * `@PathVariable`/`@RequestParam` sai kiểu — vd UUID không hợp lệ trên path.
     *
     * <p>Không có handler này, ngoại lệ rơi vào lưới an toàn cuối cùng và trả về 500 — sai ngữ
     * nghĩa cho một lỗi đầu vào rõ ràng của client.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "Tham số '%s' có giá trị '%s' không đúng định dạng mong đợi."
                .formatted(ex.getName(), ex.getValue());
        return problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Tham số không hợp lệ", detail, request);
    }

    /** JSON body gửi lên không đọc được (cú pháp hỏng, kiểu sai). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "malformed-request-body",
                "Nội dung yêu cầu không đọc được",
                "Kiểm tra lại định dạng JSON của request body.",
                request);
    }

    /** Validate `@RequestParam`/`@PathVariable` qua Bean Validation (khác `@Valid` trên body). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        jakarta.validation.ConstraintViolation::getMessage,
                        (first, second) -> first));

        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST, "validation-failed", "Dữ liệu đầu vào không hợp lệ", null, request);
        detail.setProperty("fieldErrors", violations);
        return detail;
    }

    /** File upload vượt {@code MAX_UPLOAD_SIZE} — không có handler này, Tomcat/Spring trả về 500. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "upload-too-large",
                "File tải lên quá lớn",
                "Kích thước file vượt quá giới hạn cho phép.",
                request);
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
        return ProblemDetails.of(status, type, title, detail, req.getRequestURI());
    }
}
