package vn.hust.datn.casestudy.vulnshop.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ghi nhật ký hành động của người dùng.
 *
 * <p>Lớp này KHÔNG có test — có chủ đích, để cảnh báo ở đây không có bằng chứng kiểm thử đi kèm.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    /**
     * SEEDED — lỗi thật: chèn dữ liệu người dùng thẳng vào bản ghi nhật ký.
     *
     * <p>{@code username} chưa được làm sạch. Kẻ tấn công gửi tên chứa ký tự xuống dòng có thể
     * chèn thêm dòng nhật ký giả, làm sai lệch hồ sơ điều tra sự cố.
     */
    public void logLogin(String username, String ipAddress) {
        log.info("Dang nhap thanh cong: user=" + username + " ip=" + ipAddress);
    }

    /** Ghi nhật ký đúng cách: dùng tham số hoá của SLF4J. */
    public void logLogout(String username) {
        log.info("Dang xuat: user={}", username);
    }
}
