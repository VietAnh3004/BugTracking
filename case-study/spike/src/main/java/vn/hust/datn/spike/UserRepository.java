package vn.hust.datn.spike;

import java.util.HashMap;
import java.util.Map;

/**
 * Lớp mồi cho spike, cố tình chứa cả lỗi thật lẫn đoạn mã dễ bị báo nhầm.
 *
 * <p>Mục đích duy nhất là sinh ra cảnh báo, kết quả test và số liệu độ phủ để kiểm tra xem ba
 * nguồn đó có trỏ về cùng một file:line hay không.
 */
public class UserRepository {

    private final Map<String, String> users = new HashMap<>();

    public void addUser(String username, String email) {
        users.put(username, email);
    }

    /**
     * Lỗi thật: trả về null khi không tìm thấy, rồi gọi toLowerCase() lên kết quả đó.
     *
     * <p>Kỳ vọng: SpotBugs báo NP_NULL_ON_SOME_PATH tại dòng gọi toLowerCase(), và test
     * findEmailDomain_khongTonTai_nemNPE ném NullPointerException với stack trace trỏ về đúng
     * dòng đó. Nếu hai vị trí này khớp nhau thì cơ chế liên kết theo stack trace là khả thi.
     */
    public String findEmailDomain(String username) {
        String email = users.get(username);
        return email.toLowerCase().split("@")[1];
    }

    /**
     * Nhánh này không bao giờ nhận null vì mọi lời gọi đều đi qua addUser đã kiểm tra.
     *
     * <p>Đây là kiểu mã sinh ra cảnh báo nhưng không phải lỗi thật — nguồn false positive tự
     * nhiên cho case study.
     */
    public int countUsersWithDomain(String domain) {
        int count = 0;
        for (Map.Entry<String, String> entry : users.entrySet()) {
            String email = entry.getValue();
            if (email != null && email.endsWith(domain)) {
                count++;
            }
        }
        return count;
    }
}
