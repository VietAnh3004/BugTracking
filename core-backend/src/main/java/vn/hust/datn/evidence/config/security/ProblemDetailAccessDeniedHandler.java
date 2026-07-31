package vn.hust.datn.evidence.config.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.exception.ProblemDetails;

/** 403 khi đã xác thực nhưng thiếu quyền (vd JWT người dùng gọi {@code /api/v1/tools/**}). */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.debug("Tu choi truy cap tai {} {}: {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        ProblemDetail problem =
                ProblemDetails.of(HttpStatus.FORBIDDEN, "forbidden", "Không đủ quyền", null, request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
