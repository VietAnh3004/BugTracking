package vn.hust.datn.evidence.exception;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Helper dựng {@link ProblemDetail} dùng chung giữa {@link GlobalExceptionHandler} (lỗi trong
 * {@code DispatcherServlet}) và các handler bảo mật (lỗi trong servlet filter, xem
 * {@code vn.hust.datn.evidence.config.security.ProblemDetailAuthenticationEntryPoint}).
 *
 * <p>401/403 phát sinh trong filter chain của Spring Security KHÔNG đi qua
 * {@code @RestControllerAdvice} — nếu mỗi nơi tự dựng ProblemDetail riêng, hai định dạng lỗi sẽ
 * trôi xa nhau sau vài tháng. Tách ra đây để chỉ có đúng MỘT nơi định nghĩa shape lỗi.
 */
public final class ProblemDetails {

    private static final String TYPE_PREFIX = "https://datn.hust.vn/problems/";

    private ProblemDetails() {}

    public static ProblemDetail of(HttpStatus status, String type, String title, String detail, String requestUri) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        if (detail != null) {
            problem.setDetail(detail);
        }
        problem.setInstance(URI.create(requestUri));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
