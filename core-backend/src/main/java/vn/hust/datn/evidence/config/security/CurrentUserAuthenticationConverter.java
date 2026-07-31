package vn.hust.datn.evidence.config.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.domain.UserRole;

/**
 * Dựng {@link CurrentUserAuthenticationToken} từ claim {@code sub}/{@code email}/{@code role} của
 * JWT đã qua validate (chữ ký, {@code exp}, {@code iss}).
 *
 * <p>Cố tình KHÔNG dùng {@code HandlerMethodArgumentResolver} tự viết để bind
 * {@code @AuthenticationPrincipal}: springdoc có auto-config bỏ qua tham số đó khi phát hiện Spring
 * Security trên classpath, còn resolver tự viết thì springdoc sẽ thêm một query parameter lạ vào
 * MỌI endpoint trong spec OpenAPI — làm bẩn {@code openapi.json}/{@code api-types.ts}. Converter
 * này chỉ tham gia vào bước xác thực, không đụng gì tới cơ chế bind tham số.
 */
@Component
public class CurrentUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        UserRole role = UserRole.valueOf(jwt.getClaimAsString("role"));
        CurrentUser principal = new CurrentUser(id, email, role);
        return new CurrentUserAuthenticationToken(jwt, principal, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
