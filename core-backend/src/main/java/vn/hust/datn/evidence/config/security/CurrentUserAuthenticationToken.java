package vn.hust.datn.evidence.config.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@code Authentication} có {@code getPrincipal()} trả về {@link CurrentUser} thay vì {@link Jwt}
 * trần — khác với {@code JwtAuthenticationToken} mặc định của Spring Security. Nhờ vậy controller
 * dùng {@code @AuthenticationPrincipal CurrentUser} thẳng, không phải tự bóc claim ở từng nơi.
 */
public class CurrentUserAuthenticationToken extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final CurrentUser principal;

    public CurrentUserAuthenticationToken(Jwt jwt, CurrentUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.jwt = jwt;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
