package vn.hust.datn.casestudy.vulnshop.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Băm mật khẩu và tính checksum tệp.
 *
 * <p>Hai phương thức trong lớp này dùng cùng một thuật toán MD5 nhưng mang ý nghĩa hoàn toàn khác
 * nhau. Đây là chủ đích: công cụ phân tích tĩnh sẽ báo cả hai, nhưng chỉ một là lỗi thật. Muốn
 * phân biệt phải đọc xem giá trị băm được dùng vào việc gì — điều mà cảnh báo đơn lẻ không nói ra.
 */
@Component
public class PasswordHasher {

    /**
     * SEEDED — lỗi thật: dùng MD5 để băm mật khẩu.
     *
     * <p>MD5 nhanh và đã bị phá, nên rất dễ tấn công từ điển; lại còn không có salt. Mật khẩu phải
     * dùng bcrypt, scrypt hoặc Argon2. Kỳ vọng Semgrep và find-sec-bugs bắt (WEAK_MESSAGE_DIGEST).
     */
    public String hashPassword(String rawPassword) {
        return md5Hex(rawPassword);
    }

    /**
     * SEEDED — false positive: MD5 dùng để tính checksum phát hiện trùng tệp.
     *
     * <p>Công cụ sẽ báo "weak hashing algorithm" y hệt trường hợp trên. Nhưng ở đây MD5 không đóng
     * vai trò bảo mật: nó chỉ dùng để nhận biết hai tệp upload có nội dung giống nhau hay không.
     * Kẻ tấn công tạo được va chạm MD5 cũng không thu được gì.
     *
     * <p>Vẫn nên đổi sang SHA-256 nếu tiện, nhưng đây không phải lỗ hổng — đúng nghĩa false positive.
     */
    public String checksumForDeduplication(byte[] fileContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(fileContent));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Moi JVM deu phai co MD5", e);
        }
    }

    public boolean matches(String rawPassword, String expectedHash) {
        return md5Hex(rawPassword).equals(expectedHash);
    }

    private String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Moi JVM deu phai co MD5", e);
        }
    }
}
