package vn.hust.datn.evidence.config.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Xác thực {@code /api/v1/tools/**} bằng một bí mật chia sẻ trong header {@code X-Service-Token},
 * KHÔNG dùng {@code Authorization: Bearer} — xem ADR 0005 điểm 2 (agent-service không forward JWT
 * người dùng). Dùng tên header khác hẳn làm ranh giới hai cơ chế xác thực hiển nhiên khi đọc log,
 * tránh việc ai đó vô tình gửi JWT người dùng vào đây và nó "gần như" chạy được.
 *
 * <p>So khớp bằng {@link MessageDigest#isEqual(byte[], byte[])} (thời gian hằng), không
 * {@code String.equals} — tránh timing attack dò từng ký tự của token.
 *
 * <p>Không có hạn dùng, không tự xoay: chỉ một caller máy-với-máy, bí mật chia sẻ qua biến môi
 * trường. Đánh đổi bù bằng việc xoay {@code SERVICE_TOKEN} thủ công khi cần và không bao giờ ghi
 * token ra log.
 */
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Service-Token";

    private final String expectedToken;

    public ServiceTokenAuthenticationFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);
        if (provided != null && constantTimeEquals(provided, expectedToken)) {
            AbstractAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                    "agent-service", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
            authentication.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
