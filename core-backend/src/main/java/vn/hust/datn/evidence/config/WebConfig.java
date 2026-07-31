package vn.hust.datn.evidence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS chuyển sang {@link CorsConfig} (gắn vào Spring Security từ tuần 5-7) — xem Javadoc ở đó để
 * biết lý do {@code addCorsMappings} không còn dùng được nữa khi Security đã bật. Giữ cả hai
 * nguồn cấu hình CORS song song là mời gọi chúng lệch nhau âm thầm, nên method đó bị xoá hẳn khỏi
 * đây thay vì để trống.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ToolCallLoggingInterceptor()).addPathPatterns("/api/v1/tools/**");
    }
}
