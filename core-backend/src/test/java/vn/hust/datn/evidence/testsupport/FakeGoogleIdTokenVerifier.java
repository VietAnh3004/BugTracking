package vn.hust.datn.evidence.testsupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import vn.hust.datn.evidence.exception.InvalidGoogleTokenException;
import vn.hust.datn.evidence.service.account.GoogleIdTokenVerifier;
import vn.hust.datn.evidence.service.account.GoogleIdentity;

/**
 * "Google" của bộ test: test đăng ký trước chuỗi token ↔ danh tính, mọi chuỗi khác bị từ chối như
 * một token giả mạo. Phần kiểm chữ ký/aud/iss THẬT nằm ở {@code NimbusGoogleIdTokenVerifierTest}.
 */
public class FakeGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private final Map<String, GoogleIdentity> tokens = new ConcurrentHashMap<>();

    public String register(GoogleIdentity identity) {
        String token = "fake-google-token-" + identity.subject();
        tokens.put(token, identity);
        return token;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        GoogleIdentity identity = tokens.get(idToken);
        if (identity == null) {
            throw new InvalidGoogleTokenException();
        }
        return identity;
    }
}
