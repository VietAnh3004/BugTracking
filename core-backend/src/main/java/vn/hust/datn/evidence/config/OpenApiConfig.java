package vn.hust.datn.evidence.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Spec OpenAPI là hợp đồng giữa frontend và backend.
 *
 * <p>Frontend sinh client TypeScript từ spec này, nên hai bên không thể lệch kiểu dữ liệu mà
 * không bị phát hiện lúc biên dịch. Mọi endpoint nằm dưới {@code /api/v1}; thay đổi phá vỡ tương
 * thích phải tạo {@code v2} thay vì sửa {@code v1}, để frontend bản cũ vẫn chạy được khi backend
 * đã lên bản mới.
 *
 * <p><b>{@code servers} bắt buộc khai báo tường minh.</b> Không khai thì springdoc tự sinh từ
 * request hiện tại — dưới MockMvc là {@code http://localhost}, chạy thật là {@code :8080}, deploy
 * là domain khác — khiến snapshot test không bao giờ khớp giữa các môi trường. URL tương đối
 * {@code "/"} đúng với mọi môi trường và hợp với kiến trúc FE/BE tách domain (FE tự ghép
 * {@code apiBaseUrl} từ {@code window.__APP_CONFIG__}, không đọc {@code servers} của spec).
 *
 * <p>Tag khai tường minh (không để springdoc tự gom từ thứ tự scan controller) để output ổn định
 * giữa các lần chạy — điều kiện cần cho snapshot test đối chiếu {@code docs/api/openapi.json}.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Hai security scheme:
     *
     * <ul>
     *   <li>{@code bearerAuth} — JWT của người dùng, áp dụng MẶC ĐỊNH cho mọi endpoint (qua {@code
     *       addSecurityItem} toàn cục). Endpoint công khai (đăng ký/đăng nhập/refresh) phải tự gắn
     *       {@code @SecurityRequirements()} rỗng để xoá yêu cầu này.
     *   <li>{@code serviceToken} — header {@code X-Service-Token}, chỉ dùng cho {@code
     *       /api/v1/tools/**}. Controller đó gắn {@code @SecurityRequirements(@SecurityRequirement(
     *       name = "serviceToken"))} để THAY THẾ (không cộng thêm) yêu cầu {@code bearerAuth} toàn
     *       cục — hai cơ chế xác thực này loại trừ lẫn nhau ở tầng Spring Security (SecurityConfig),
     *       spec phải phản ánh đúng thực tế đó.
     * </ul>
     */
    @Bean
    public OpenAPI evidenceAgentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Evidence-Based Program Analysis API")
                        .version("v1")
                        .description("Evidence store, phân tích tĩnh, bằng chứng kiểm thử và human review.")
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/")))
                .tags(List.of(
                        new Tag().name("projects").description("Quản lý dự án được phân tích."),
                        new Tag()
                                .name("evidence-tools")
                                .description(
                                        "5 evidence tool mà Agent Service gọi. RPC theo tên tool, không phải REST"
                                                + " resource — xem ADR 0005.")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(
                                "serviceToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Service-Token")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
