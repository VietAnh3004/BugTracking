package vn.hust.datn.evidence.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.config.properties.JwtProperties;
import vn.hust.datn.evidence.domain.RefreshToken;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.repository.RefreshTokenRepository;

/**
 * Phát access token (JWT) và refresh token (opaque, lưu hash) — "cách mint token", khác với
 * {@code RefreshTokenService} (tuần 5-7, bước sau) là "trạng thái xoay vòng/thu hồi".
 *
 * <p>Access token = JWT HS256, claim {@code sub}=userId, {@code email}, {@code role}. Refresh
 * token = 32 byte {@link SecureRandom} mã hoá Base64URL, lưu DB dưới dạng SHA-256 hex — xem
 * Javadoc {@code refresh_token} ở migration V3 để biết vì sao không phải BCrypt.
 */
@Service
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, RefreshTokenRepository refreshTokenRepository) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiration()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        // JwsHeader phải khai tường minh HS256 - JwtEncoderParameters.from(claims) một tham số
        // mặc định RS256, không khớp JWKSource chỉ có khoá đối xứng (xem CLAUDE.md).
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Tạo refresh token MỚI, mở một family mới — dùng lúc đăng ký/đăng nhập. */
    @Transactional
    public String issueInitialRefreshToken(UUID userId, String userAgent) {
        return issueRefreshToken(userId, UUID.randomUUID(), userAgent);
    }

    /** Tạo refresh token nối tiếp trong CÙNG một family — dùng khi xoay vòng (B5). */
    @Transactional
    public String issueRefreshToken(UUID userId, UUID familyId, String userAgent) {
        String plaintext = generateOpaqueToken();
        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hashRefreshToken(plaintext));
        entity.setFamilyId(familyId);
        entity.setIssuedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(jwtProperties.refreshExpiration()));
        entity.setUserAgent(userAgent);
        refreshTokenRepository.save(entity);
        return plaintext;
    }

    public long accessTokenExpiresInSeconds() {
        return jwtProperties.expiration().getSeconds();
    }

    /** Dùng chung bởi {@code RefreshTokenService} (B5) để tra cứu theo hash khi xoay vòng. */
    public String hashRefreshToken(String plaintextToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plaintextToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn có trên mọi JVM chuẩn - không phải lỗi cấu hình có thể xảy ra thật.
            throw new IllegalStateException("SHA-256 không khả dụng trên JVM này", e);
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
