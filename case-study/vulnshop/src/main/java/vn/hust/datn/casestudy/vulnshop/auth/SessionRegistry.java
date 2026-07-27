package vn.hust.datn.casestudy.vulnshop.auth;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Theo dõi phiên đăng nhập đang hoạt động.
 *
 * <p>Lớp này KHÔNG có test nào — có chủ đích. Cảnh báo sinh ra ở đây sẽ không có bằng chứng kiểm
 * thử đi kèm, tạo ra trường hợp {@code INSUFFICIENT_EVIDENCE} cho hệ thống đánh giá. Nếu mọi dòng
 * đều được test phủ thì verdict đó không bao giờ xuất hiện được trong demo.
 */
@Component
public class SessionRegistry {

    /**
     * SEEDED — lỗi thật: trạng thái tĩnh có thể thay đổi, không đồng bộ.
     *
     * <p>HashMap tĩnh bị nhiều luồng xử lý request cùng ghi. HashMap không an toàn với đa luồng:
     * ghi đồng thời có thể làm hỏng cấu trúc bên trong và gây vòng lặp vô hạn khi đọc.
     * Kỳ vọng SpotBugs bắt (MS_MUTABLE_COLLECTION hoặc tương đương).
     */
    public static final Map<String, Long> ACTIVE_SESSIONS = new HashMap<>();

    public void register(String sessionId, long userId) {
        ACTIVE_SESSIONS.put(sessionId, userId);
    }

    public void invalidate(String sessionId) {
        ACTIVE_SESSIONS.remove(sessionId);
    }

    public int activeCount() {
        return ACTIVE_SESSIONS.size();
    }
}
