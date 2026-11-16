package vn.hust.datn.evidence.mapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import vn.hust.datn.evidence.domain.User;

/**
 * Ảnh đại diện không lưu trên hệ thống này: ảnh Google nếu đã liên kết, không thì Gravatar.
 *
 * <p>Gravatar nhận SHA-256 của email đã {@code trim} + chữ thường (MD5 cũ vẫn chạy nhưng đã bị
 * Gravatar khuyên bỏ). {@code d=404}: chưa có ảnh trên Gravatar thì trả 404 thay vì một hình mặc
 * định của Gravatar — frontend tự hiện chữ cái đầu cho đồng bộ với phần còn lại của giao diện.
 * Người dùng đổi ảnh bằng cách đăng ký/đổi ảnh tại gravatar.com với đúng email này.
 */
public final class Avatars {

    static final String GRAVATAR_BASE = "https://gravatar.com/avatar/";

    private Avatars() {}

    public static String urlFor(User user) {
        if (user.getGooglePictureUrl() != null && !user.getGooglePictureUrl().isBlank()) {
            return user.getGooglePictureUrl();
        }
        return gravatarUrl(user.getEmail());
    }

    public static String gravatarUrl(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return GRAVATAR_BASE + HexFormat.of().formatHex(hash) + "?s=160&d=404";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng trên JVM này", e);
        }
    }
}
