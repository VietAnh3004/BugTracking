package vn.hust.datn.evidence.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import vn.hust.datn.evidence.config.properties.AccountProperties;
import vn.hust.datn.evidence.exception.InvalidGoogleTokenException;

/**
 * Bốn điều kiện kiểm Google ID token, mỗi điều kiện một test hỏng riêng — thiếu điều kiện nào thì
 * test của nó xanh giả, nên phải thử từng cái. Khoá RSA tự sinh thay cho JWKS của Google.
 */
class NimbusGoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "ung-dung-nay.apps.googleusercontent.com";

    private static RSAPrivateKey privateKey;
    private static RSAPrivateKey otherPrivateKey;
    private static NimbusGoogleIdTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        otherPrivateKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();

        AccountProperties properties = new AccountProperties(
                "http://app", Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofSeconds(60), null, null, false, CLIENT_ID);
        verifier = new NimbusGoogleIdTokenVerifier(
                properties, NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build());
    }

    @Test
    void tokenHopLeTraVeDanhTinh() throws Exception {
        GoogleIdentity identity = verifier.verify(sign(claims().build(), privateKey));

        assertThat(identity.subject()).isEqualTo("1234567890");
        assertThat(identity.email()).isEqualTo("a@gmail.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.name()).isEqualTo("Nguyen Van A");
    }

    @Test
    void tokenCapChoUngDungKhacBiTuChoi() throws Exception {
        String token = sign(claims().audience("ung-dung-khac.apps.googleusercontent.com").build(), privateKey);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void issuerKhongPhaiGoogleBiTuChoi() throws Exception {
        String token = sign(claims().issuer("https://gia-mao.example").build(), privateKey);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void tokenHetHanBiTuChoi() throws Exception {
        String token = sign(
                claims().expirationTime(Date.from(Instant.now().minus(Duration.ofHours(2)))).build(), privateKey);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void chuKyKhoaKhacBiTuChoi() throws Exception {
        String token = sign(claims().build(), otherPrivateKey);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void emailChuaXacThucDocRaFalse() throws Exception {
        GoogleIdentity identity = verifier.verify(sign(claims().claim("email_verified", false).build(), privateKey));

        assertThat(identity.emailVerified()).isFalse();
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("1234567890")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
                .claim("email", "a@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Nguyen Van A");
    }

    private static String sign(JWTClaimsSet claims, RSAPrivateKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
