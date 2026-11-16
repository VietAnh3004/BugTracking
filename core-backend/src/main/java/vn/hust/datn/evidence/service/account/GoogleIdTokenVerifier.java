package vn.hust.datn.evidence.service.account;

/**
 * Kiểm một Google ID token (chuỗi {@code credential} mà nút "Đăng nhập bằng Google" trả về cho
 * frontend). Interface để test thay bằng bản giả — test không gọi được Google thật.
 */
public interface GoogleIdTokenVerifier {

    /**
     * @throws vn.hust.datn.evidence.exception.InvalidGoogleTokenException chữ ký sai, hết hạn, sai
     *     issuer hoặc token cấp cho ứng dụng khác
     */
    GoogleIdentity verify(String idToken);
}
