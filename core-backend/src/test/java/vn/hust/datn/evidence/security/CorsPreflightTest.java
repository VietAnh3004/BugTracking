package vn.hust.datn.evidence.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;

/**
 * {@code WebConfig.addCorsMappings} đăng ký CORS ở tầng {@code HandlerMapping} (bên trong {@code
 * DispatcherServlet}), còn Spring Security là servlet filter chạy TRƯỚC đó. Preflight {@code
 * OPTIONS} không mang header {@code Authorization} (đặc tả CORS cấm) — nếu CORS không được xử lý
 * ở tầng filter, {@code anyRequest().authenticated()} sẽ trả 401 cho preflight, trình duyệt báo
 * lỗi "CORS policy" (chứ không phải 401) và người debug đi sửa nhầm chỗ. Đây là bộ test canh
 * đúng cái bẫy đó — xem {@link vn.hust.datn.evidence.config.CorsConfig}.
 */
class CorsPreflightTest extends AbstractWebIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Test
    void preflightOptionsTuOriginChoPhepTraVe200MacDuKhongCoToken() throws Exception {
        getMockMvc()
                .perform(options("/api/v1/projects")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void preflightTuOriginLaKhongCoHeaderChoPhep() throws Exception {
        getMockMvc()
                .perform(options("/api/v1/projects")
                        .header(HttpHeaders.ORIGIN, "https://origin-la.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void phanHoi401VanCoHeaderAccessControlAllowOrigin() throws Exception {
        // Nếu thiếu header này, FE thấy "CORS error" thay vì đọc được status 401 và không bao
        // giờ chuyển được sang màn đăng nhập - CorsFilter phải chạy TRƯỚC entry point 401.
        getMockMvc()
                .perform(get("/api/v1/projects").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    /**
     * Canh một bug ĐÃ GẶP THẬT: `CorsConfig` ban đầu chỉ đăng ký `/api/**`, nên `/actuator/health`
     * — request ĐẦU TIÊN mà frontend gửi đi (xem `useBackendHealth`) — không có header CORS nào và
     * bị trình duyệt chặn ngay khi mở ứng dụng, trong khi mọi endpoint `/api/**` vẫn chạy tốt.
     *
     * <p>Kiểm bằng `curl` không phát hiện được: `curl` không ép chính sách same-origin, nó trả về
     * 200 bình thường. Chỉ trình duyệt thật mới lộ ra.
     */
    @Test
    void actuatorHealthCoHeaderCorsVaFrontendGoiDuoc() throws Exception {
        getMockMvc()
                .perform(get("/actuator/health").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void preflightToiActuatorHealthTraVe200() throws Exception {
        getMockMvc()
                .perform(options("/actuator/health")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void preflightToiApiToolsBiTuChoi() throws Exception {
        // toolChain tắt hẳn CORS (agent-service gọi server-to-server, không có Origin) - preflight
        // không nhận được Access-Control-Allow-Origin, trình duyệt sẽ tự chặn request thật.
        getMockMvc()
                .perform(options("/api/v1/tools/get_warning")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
