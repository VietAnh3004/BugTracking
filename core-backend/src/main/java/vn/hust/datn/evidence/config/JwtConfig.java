package vn.hust.datn.evidence.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import vn.hust.datn.evidence.config.properties.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * {@code JwtEncoder}/{@code JwtDecoder} dùng chung một khoá đối xứng HS256 từ {@link
 * JwtProperties#secret()}.
 *
 * <p>Đây KHÔNG phải OAuth2 thật — không có Authorization Server nào. Ta chỉ mượn hạ tầng
 * {@code spring-boot-starter-oauth2-resource-server} vì nó cho sẵn {@code
 * BearerTokenAuthenticationFilter}, validate {@code exp}/{@code iss} qua {@link
 * OAuth2TokenValidator}, 401 chuẩn RFC 6750, và springdoc tự bỏ qua tham số
 * {@code @AuthenticationPrincipal} khi phát hiện Spring Security trên classpath — tất cả những
 * thứ đó phải tự viết nếu chọn thư viện JWT trần (jjwt/nimbus).
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final JwtProperties jwtProperties;

    @Bean
    public JwtEncoder jwtEncoder() {
        // keyID + keyUse bắt buộc phải khai tường minh: thiếu một trong hai, NimbusJwtEncoder
        // không chọn được JWK nào khớp lúc mã hoá (JwtEncodingException "Failed to select a JWK
        // signing key") dù khoá và thuật toán đều đúng — lỗi chỉ lộ ra lúc gọi encode(), không
        // lộ lúc khởi động ứng dụng.
        OctetSequenceKey key = new OctetSequenceKey.Builder(secretKeyBytes())
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("hmac-primary")
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secretKeyBytes(), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> withIssuer =
                JwtValidators.createDefaultWithIssuer(jwtProperties.issuer());
        decoder.setJwtValidator(withIssuer);
        return decoder;
    }

    private byte[] secretKeyBytes() {
        return jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    }
}
