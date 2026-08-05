package evidence.canary;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Mã mồi có lỗi BIẾT TRƯỚC, biên dịch sẵn trong image và đưa vào CÙNG MỘT lời gọi SpotBugs với mã
 * của người dùng.
 *
 * <p>Vì sao cần: bốn tầng chống "chạy xong nhưng 0 cảnh báo" (ADR 0008) không thay thế được nhau.
 * Tầng 2 đọc locator trong artifact và chứng minh "analyzer đọc được class của người dùng"; canary
 * chứng minh thứ khác hẳn — <b>bộ rule thật sự còn sống</b>. Kịch bản phân biệt hai tầng: nếu
 * find-sec-bugs không nạp được (đã xảy ra thật một lần trong dự án này, do khai sai chỗ trong
 * pom), tầng 2 vẫn xanh vì SpotBugs core vẫn chạy và vẫn quét đủ class — chỉ canary đỏ.
 *
 * <p>Ba lỗi dưới đây được chọn để phủ CẢ HAI bộ rule. Thiếu một trong hai bộ là hỏng khác nhau:
 *
 * <ul>
 *   <li>{@link #weakHash} → {@code WEAK_MESSAGE_DIGEST_MD5} — <b>find-sec-bugs</b>
 *   <li>{@link #sqlInjection} → {@code SQL_INJECTION_JDBC} — <b>find-sec-bugs</b>
 *   <li>{@link #defaultEncoding} → {@code DM_DEFAULT_ENCODING} — <b>SpotBugs core</b>
 * </ul>
 *
 * <p>Finding sinh ra từ file này bị LOẠI khỏi {@code finding_count} theo tên package
 * {@code evidence.canary} — nó không phải cảnh báo của repo người dùng.
 *
 * <p><b>Đừng "sửa" các lỗi ở đây.</b> Chúng là thiết bị đo, không phải khiếm khuyết. Cùng tinh
 * thần với năm test hỏng của vulnshop.
 */
public final class Canary {

    private Canary() {}

    /** find-sec-bugs: WEAK_MESSAGE_DIGEST_MD5. */
    public static byte[] weakHash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return digest.digest(input.getBytes());
    }

    /** find-sec-bugs: SQL_INJECTION_JDBC — nối chuỗi thẳng vào câu truy vấn. */
    public static ResultSet sqlInjection(Connection connection, String username) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeQuery("SELECT * FROM app_user WHERE username = '" + username + "'");
    }

    /** SpotBugs core: DM_DEFAULT_ENCODING — phụ thuộc charset mặc định của nền tảng. */
    public static String defaultEncoding(byte[] raw) {
        return new String(raw);
    }
}
