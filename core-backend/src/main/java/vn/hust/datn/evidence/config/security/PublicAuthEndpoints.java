package vn.hust.datn.evidence.config.security;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Các endpoint {@code /api/v1/auth/**} công khai — MỘT danh sách, dùng cho cả {@code permitAll}
 * lẫn {@link #bearerTokenResolver()} trong {@code SecurityConfig}, để hai chỗ không bao giờ lệch.
 *
 * <h2>Vì sao phải bỏ qua header Authorization ở đây</h2>
 *
 * {@code permitAll} KHÔNG có nghĩa "không kiểm token". {@code BearerTokenAuthenticationFilter}
 * chạy trước bước phân quyền: thấy {@code Authorization: Bearer ...} là nó giải mã, và token hết
 * hạn hay sai chữ ký thì trả 401 {@code unauthenticated} NGAY, request không tới được controller.
 *
 * <p>Hậu quả gặp thật (18/11/2026): access token sống 15 phút; hết hạn giữa phiên thì frontend gọi
 * {@code /auth/refresh} — kèm luôn access token vừa hết hạn — và bị chặn ở đúng cửa đó. Refresh
 * token còn hạn 14 ngày mà không bao giờ được dùng; người dùng bị đẩy về trang đăng nhập sau mỗi
 * 15 phút dùng liên tục, không để lại dấu vết nào trong DB hay log. Lúc tải lại trang thì không
 * gặp, vì khi đó trình duyệt chưa có access token để gửi.
 *
 * <p>Các endpoint này xác thực bằng thứ trong THÂN request (mật khẩu, refresh token, mã trong
 * email, credential Google), nên header Authorization ở đây vô nghĩa — bỏ qua là đúng, không phải
 * nới lỏng bảo mật.
 *
 * <p>{@code /auth/logout} cũng công khai, cùng lý do: trước 18/11/2026 nó đòi JWT còn hạn, nên bấm
 * "Đăng xuất" sau 15 phút không thao tác thì nhận 401, frontend bỏ qua lỗi (vẫn phải đăng xuất phía
 * client) và refresh token KHÔNG bị thu hồi — còn sống thêm 14 ngày trong DB. Có refresh token
 * trong body đã là bằng chứng sở hữu phiên; người cầm được token đó vốn đã tự refresh được, nên
 * cho họ thu hồi nó không mở thêm quyền gì.
 */
public final class PublicAuthEndpoints {

    public static final List<String> POST_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/google",
            "/api/v1/auth/logout");

    public static final List<String> GET_PATHS = List.of("/api/v1/auth/config");

    private PublicAuthEndpoints() {}

    static boolean matches(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (HttpMethod.POST.matches(request.getMethod())) {
            return POST_PATHS.contains(path);
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            return GET_PATHS.contains(path);
        }
        return false;
    }

    /** Đọc Bearer token như mặc định, trừ ở endpoint công khai: ở đó coi như không có header. */
    public static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> matches(request) ? null : delegate.resolve(request);
    }
}
