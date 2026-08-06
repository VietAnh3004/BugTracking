package vn.hust.datn.casestudy.vulnshop.user;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.hust.datn.casestudy.vulnshop.domain.User;

/** Nghiệp vụ liên quan tới người dùng. */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * SEEDED — lỗi thật: NullPointerException khi không tìm thấy người dùng.
     *
     * <p>{@code findFirst} trả về null nếu danh sách rỗng, rồi {@code email()} được gọi ngay trên
     * kết quả đó.
     *
     * <p><b>Điểm quan trọng của case study:</b> kỳ vọng phân tích tĩnh KHÔNG bắt được lỗi này —
     * spike tuần 1 đã cho thấy SpotBugs bỏ qua đúng dạng NPE này. Chỉ có test hỏng kèm stack trace
     * mới chứng minh được. Đây là bằng chứng thực nghiệm trực tiếp cho luận điểm trung tâm của đồ
     * án: một nguồn bằng chứng là không đủ.
     */
    public String emailDomainOf(String username) {
        User user = findFirst(username);
        return user.email().split("@")[1];
    }

    /**
     * SEEDED — false positive: kiểm tra null thừa.
     *
     * <p>{@code userRepository.findByRole} luôn trả về danh sách (rỗng chứ không null), nên nhánh
     * kiểm tra null không bao giờ chạy. Công cụ có thể báo RCN_REDUNDANT_NULLCHECK. Đây là mã
     * phòng thủ dư thừa, không phải lỗi — không gây hại gì ngoài việc rườm rà.
     */
    public int countByRole(String role) {
        List<User> users = userRepository.findByRole(role);
        if (users == null) {
            return 0;
        }
        return users.size();
    }

    /**
     * SEEDED — lỗi thật, và là ca ĐỐI CHỨNG của {@link #emailDomainOf(String)}.
     *
     * <p>Cùng một lỗi NullPointerException, nhưng lần này phép kiểm null nằm NGAY TRONG method
     * (ở biểu thức ba ngôi) rồi giá trị vẫn được giải tham chiếu ở dòng sau. SpotBugs nhìn thấy
     * nhánh mà {@code user} chắc chắn null vẫn đi tới chỗ gọi {@code user.email()}, nên nó bắt
     * được — khác hẳn {@code emailDomainOf}, nơi việc trả về null nằm trong một method riêng.
     *
     * <p><b>Vì sao cần ca này:</b> nó là trường hợp DUY NHẤT trong vulnshop mà một cảnh báo tĩnh
     * và một stack trace của test hỏng cùng trỏ về ĐÚNG MỘT DÒNG. Không có nó, quan hệ
     * {@code SUPPORTS} suy từ stack frame — loại bằng chứng thuyết phục nhất cho verdict
     * {@code LIKELY_TRUE_POSITIVE} — không có mẫu nào để demo hay để đo.
     *
     * <p>Kỳ vọng SpotBugs bắt (NP_NULL_ON_SOME_PATH).
     */
    public String displayNameOf(String username) {
        User user = findFirst(username);
        String prefix = user == null ? "?" : user.username();
        return prefix + " <" + user.email() + ">";
    }

    private User findFirst(String username) {
        List<User> matches = userRepository.findByUsername(username);
        return matches.isEmpty() ? null : matches.get(0);
    }
}
