package vn.hust.datn.evidence.config.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.exception.ProblemDetails;

/**
 * 401 cho request chưa xác thực hoặc token không hợp lệ.
 *
 * <p>Chạy trong servlet filter, TRƯỚC {@code DispatcherServlet}, nên không đi qua
 * {@code @RestControllerAdvice} — phải tự ghi {@link ProblemDetail} ra response. Dùng chung shape
 * lỗi với {@link vn.hust.datn.evidence.exception.GlobalExceptionHandler} qua {@link
 * ProblemDetails}, để frontend chỉ có một định dạng lỗi cần xử lý bất kể lỗi phát sinh ở đâu.
 *
 * <p>Không đưa lý do cụ thể (token hết hạn vs sai chữ ký vs thiếu hẳn) vào {@code detail} — thông
 * tin đó chỉ vào log, tránh lộ chi tiết giúp kẻ tấn công dò cơ chế xác thực.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.debug("Yeu cau chua xac thuc tai {} {}: {}", request.getMethod(), request.getRequestURI(), authException.getMessage());

        ProblemDetail problem = ProblemDetails.of(
                HttpStatus.UNAUTHORIZED, "unauthenticated", "Chưa xác thực", null, request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
