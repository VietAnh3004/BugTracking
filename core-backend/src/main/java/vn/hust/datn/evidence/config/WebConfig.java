package vn.hust.datn.evidence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.hust.datn.evidence.config.properties.CorsProperties;

import lombok.RequiredArgsConstructor;

/**
 * Cho phép frontend gọi API từ origin khác.
 *
 * <p>Không dùng {@code allowCredentials} vì xác thực đi qua header {@code Authorization} chứ không
 * qua cookie; bật cờ này sẽ chặn wildcard và không mang lại lợi ích gì ở đây.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location")
                .maxAge(3600);
    }
}
