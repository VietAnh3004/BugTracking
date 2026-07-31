package vn.hust.datn.evidence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.config.properties.SecurityProperties;
import vn.hust.datn.evidence.config.security.CurrentUserAuthenticationConverter;
import vn.hust.datn.evidence.config.security.ProblemDetailAccessDeniedHandler;
import vn.hust.datn.evidence.config.security.ProblemDetailAuthenticationEntryPoint;
import vn.hust.datn.evidence.config.security.ServiceTokenAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Hai chuỗi filter độc lập, phân biệt bằng {@code securityMatcher} — xem ADR 0005 điểm 2.
 *
 * <ol>
 *   <li>{@code toolChain} ({@code @Order(1)}, khớp trước): {@code /api/v1/tools/**} — service
 *       token qua header {@code X-Service-Token}, KHÔNG chấp nhận JWT người dùng.
 *   <li>{@code apiChain} ({@code @Order(2)}, khớp phần còn lại): JWT Bearer bắt buộc, trừ các
 *       endpoint whitelist (auth công khai, OpenAPI spec, Swagger UI, health probe).
 * </ol>
 *
 * <p>CORS gắn vào {@code apiChain} qua bean {@link CorsConfigurationSource} ({@link CorsConfig})
 * — xem Javadoc ở đó để biết vì sao {@code WebConfig.addCorsMappings} không dùng được nữa.
 * {@code toolChain} tắt hẳn CORS: agent-service gọi server-to-server, không có header
 * {@code Origin}, nên preflight vào {@code /api/v1/tools/**} bị từ chối là đúng ý muốn.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtDecoder jwtDecoder;
    private final CurrentUserAuthenticationConverter currentUserAuthenticationConverter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.bcryptStrength());
    }

    @Bean
    public ProblemDetailAuthenticationEntryPoint authenticationEntryPoint() {
        return new ProblemDetailAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public ProblemDetailAccessDeniedHandler accessDeniedHandler() {
        return new ProblemDetailAccessDeniedHandler(objectMapper);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain toolChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/tools/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ServiceTokenAuthenticationFilter(securityProperties.serviceToken()), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("ROLE_SERVICE"))
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(authenticationEntryPoint()).accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers("/api/v1/openapi", "/api/v1/openapi/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // authenticationEntryPoint phải khai TRONG oauth2ResourceServer, không chỉ ở
                // exceptionHandling(...) bên dưới: OAuth2ResourceServerConfigurer tự đăng ký
                // BearerTokenAuthenticationEntryPoint của riêng nó qua
                // ExceptionHandlingConfigurer.defaultAuthenticationEntryPointFor(...) — một cơ chế
                // ưu tiên theo request-matcher, đứng TRƯỚC entry point "mặc định" khai ở
                // exceptionHandling(). Không khai ở đây thì 401 rơi vào entry point gốc của
                // Spring Security (không trả JSON), làm test JsonPath rỗng "json can not be null".
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint())
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(currentUserAuthenticationConverter)))
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(authenticationEntryPoint()).accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }
}
