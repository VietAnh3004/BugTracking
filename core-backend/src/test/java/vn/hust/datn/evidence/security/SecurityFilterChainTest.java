package vn.hust.datn.evidence.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.config.properties.JwtProperties;

/**
 * Ép ADR 0005 và mục 3 của kế hoạch tuần 5-7 thành mã kiểm chứng được, không chỉ là câu trong tài
 * liệu: JWT người dùng không vào được {@code /api/v1/tools/**}, spec OpenAPI/Swagger UI/health
 * probe truy cập được khi chưa đăng nhập (thiếu whitelist thì {@code OpenApiSnapshotTest} và
 * healthcheck của docker-compose sẽ đỏ — ADR 0005 đã cảnh báo trước), và 401 luôn là RFC 7807.
 */
class SecurityFilterChainTest extends AbstractWebIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void khongCoTokenGoiApiDuAnTraVe401KemProblemDetail() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/unauthenticated"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void tokenKyBangKhoaKhacTraVe401() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects").header("Authorization", "Bearer " + tokenSignedWithWrongKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/unauthenticated"));
    }

    @Test
    void tokenHetHanTraVe401() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/unauthenticated"));
    }

    @Test
    void specOpenApiTruyCapDuocKhiChuaDangNhap() throws Exception {
        getMockMvc().perform(get("/api/v1/openapi")).andExpect(status().isOk());
    }

    @Test
    void swaggerUiTruyCapDuocKhiChuaDangNhap() throws Exception {
        // /swagger-ui.html redirect sang /swagger-ui/index.html - cả hai đều phải public.
        getMockMvc().perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        getMockMvc().perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void actuatorHealthKhongCanToken() throws Exception {
        // Thiếu whitelist này: healthcheck của docker-compose không bao giờ xanh, container
        // "khoẻ mạnh" bị coi là hỏng vĩnh viễn, depends_on treo cả compose.
        getMockMvc().perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void tokenNguoiDungBiTuChoiKhiGoiApiTools() throws Exception {
        // toolChain (Order 1) khớp /api/v1/tools/** TRƯỚC apiChain - JWT hợp lệ của người dùng
        // không được toolChain công nhận (không có oauth2ResourceServer trong chain đó), nên
        // request đã-xác-thực-nhưng-thiếu-quyền ROLE_SERVICE bị từ chối 403, không phải 401.
        getMockMvc()
                .perform(get("/api/v1/tools/get_warning").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/forbidden"));
    }

    private String tokenSignedWithWrongKey() throws Exception {
        byte[] wrongKey = "mot-khoa-hoan-toan-khac-cung-du-dai-32-byte!!!!".getBytes(StandardCharsets.UTF_8);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(900)))
                .claim("email", "attacker@test.local")
                .claim("role", "USER")
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(wrongKey));
        return signedJwt.serialize();
    }

    private String expiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(1800))
                .claim("email", "expired@test.local")
                .claim("role", "USER")
                .build();
        // JwsHeader PHẢI khai tường minh: JwtEncoderParameters.from(claims) một tham số để
        // headers=null, và NimbusJwtEncoder mặc định RS256 khi thiếu header - JWKSource của ta
        // chỉ có khoá HMAC nên sẽ ném "Failed to select a JWK signing key". TokenService thật
        // (B4/B5) phải làm giống hệt việc này khi phát access token.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
