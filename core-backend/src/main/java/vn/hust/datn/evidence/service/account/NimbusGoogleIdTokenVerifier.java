package vn.hust.datn.evidence.service.account;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.AccountProperties;
import vn.hust.datn.evidence.exception.InvalidGoogleTokenException;

/**
 * Kiểm Google ID token bằng {@link NimbusJwtDecoder} với JWKS công khai của Google — đúng cách
 * Google hướng dẫn cho backend, không cần thư viện {@code google-api-client}.
 *
 * <p>Bốn điều kiện, thiếu điều nào cũng là lỗ hổng:
 *
 * <ol>
 *   <li>Chữ ký RS256 khớp khoá công khai của Google (JWKS, Nimbus tự cache).
 *   <li>{@code iss} là Google.
 *   <li>{@code aud} là CHÍNH Client ID của ứng dụng này. Bỏ điều kiện này thì một ID token cấp cho
 *       BẤT KỲ trang web nào dùng "Đăng nhập bằng Google" cũng đăng nhập được vào đây — trang đó
 *       chỉ việc chuyển tiếp token của người dùng.
 *   <li>Còn hạn ({@code exp}).
 * </ol>
 *
 * <p>Không tạo decoder như một bean: đã có bean {@code JwtDecoder} HS256 cho JWT của chính hệ
 * thống, thêm bean thứ hai cùng kiểu thì resource server không biết dùng cái nào.
 */
@Component
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AccountProperties properties;
    private volatile NimbusJwtDecoder decoder;

    // Hai constructor: phai chi ro cai nao Spring dung, neu khong no di tim constructor rong.
    @Autowired
    public NimbusGoogleIdTokenVerifier(AccountProperties properties) {
        this.properties = properties;
    }

    /** Cho test: decoder dựng sẵn (khoá công khai tự sinh) thay cho JWKS thật của Google. */
    NimbusGoogleIdTokenVerifier(AccountProperties properties, NimbusJwtDecoder decoder) {
        this.properties = properties;
        this.decoder = withGoogleValidators(decoder, properties.googleClientId());
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw new InvalidGoogleTokenException();
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new InvalidGoogleTokenException();
        }
        // email_verified là boolean JSON, nhưng tài liệu cũ của Google có lúc trả chuỗi "true".
        Object verified = jwt.getClaims().get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return new GoogleIdentity(
                subject, email, emailVerified, jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                current = decoder;
                if (current == null) {
                    current = withGoogleValidators(
                            NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build(), properties.googleClientId());
                    decoder = current;
                }
            }
        }
        return current;
    }

    private static NimbusJwtDecoder withGoogleValidators(NimbusJwtDecoder decoder, String clientId) {
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss.toString())),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId))));
        return decoder;
    }
}
