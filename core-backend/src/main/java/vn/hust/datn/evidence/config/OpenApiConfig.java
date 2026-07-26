package vn.hust.datn.evidence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Spec OpenAPI là hợp đồng giữa frontend và backend.
 *
 * <p>Frontend sinh client TypeScript từ spec này, nên hai bên không thể lệch kiểu dữ liệu mà
 * không bị phát hiện lúc biên dịch. Mọi endpoint nằm dưới {@code /api/v1}; thay đổi phá vỡ tương
 * thích phải tạo {@code v2} thay vì sửa {@code v1}, để frontend bản cũ vẫn chạy được khi backend
 * đã lên bản mới.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI evidenceAgentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Evidence-Based Program Analysis API")
                        .version("v1")
                        .description("Evidence store, phân tích tĩnh, bằng chứng kiểm thử và human review.")
                        .license(new License().name("MIT")));
    }
}
