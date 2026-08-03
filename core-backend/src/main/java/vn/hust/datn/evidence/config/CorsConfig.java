package vn.hust.datn.evidence.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.hust.datn.evidence.config.properties.CorsProperties;

import lombok.RequiredArgsConstructor;

/**
 * Nguồn cấu hình CORS DUY NHẤT, gắn vào Spring Security (xem {@link SecurityConfig}) chứ không
 * phải {@code WebConfig.addCorsMappings} như trước tuần 5-7.
 *
 * <p><b>Vì sao phải chuyển sang đây</b>: {@code addCorsMappings} đăng ký CORS ở tầng {@code
 * HandlerMapping} bên trong {@code DispatcherServlet}, còn Spring Security là servlet filter chạy
 * TRƯỚC {@code DispatcherServlet}. Preflight {@code OPTIONS} không mang header {@code
 * Authorization} (đặc tả CORS cấm) — với {@code anyRequest().authenticated()}, request đó sẽ bị
 * {@code AuthorizationFilter} từ chối bằng 401 trước khi bao giờ chạy tới {@code HandlerMapping}.
 * Trình duyệt thấy preflight không phải 2xx và báo lỗi CORS, khiến người debug đi sửa nhầm chỗ.
 * {@code http.cors(...)} cài {@code CorsFilter} vào TRƯỚC {@code AuthorizationFilter} trong chain,
 * nơi preflight được trả lời và chặn lại đúng chỗ.
 *
 * <p>Header khai tường minh (không dùng {@code *}): {@code *} không kiểm chứng được bằng test, và
 * không hợp lệ nếu sau này bật {@code allowCredentials}.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Location"));
        // Header Authorization không cần allowCredentials=true — cờ đó chỉ liên quan cookie/TLS
        // client cert, không liên quan bearer token. Giữ false: đơn giản hơn và không ràng buộc
        // CORS_ORIGINS phải là danh sách tường minh (dù hiện tại đã là tường minh).
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        // `/actuator/**` BẮT BUỘC phải có, không phải "cho đủ": frontend gọi `/actuator/health`
        // ngay ở màn hình đầu tiên (xem `useBackendHealth`) để phát hiện sớm cấu hình sai. Chỉ
        // đăng ký `/api/**` thì request đó không có header CORS nào và trình duyệt chặn thẳng —
        // triệu chứng là "No 'Access-Control-Allow-Origin' header is present" ngay khi mở app,
        // trong khi mọi endpoint /api/** lại hoạt động bình thường nên rất dễ đổ lỗi nhầm cho
        // frontend. Gặp thật khi kiểm chứng bằng trình duyệt (curl không lộ ra vì curl không ép
        // chính sách same-origin).
        source.registerCorsConfiguration("/actuator/**", configuration);
        return source;
    }
}
