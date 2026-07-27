package vn.hust.datn.casestudy.vulnshop.auth;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.hust.datn.casestudy.vulnshop.domain.User;
import vn.hust.datn.casestudy.vulnshop.user.UserRepository;

/** Xác thực người dùng. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Kiểm tra thông tin đăng nhập.
     *
     * <p>Lưu ý: hàm này gọi {@code findByUsername} — chính là chỗ có SQL injection. Đường đi từ
     * tham số HTTP tới câu lệnh SQL đi qua đây, nên đây là mắt xích quan trọng khi truy vết ngữ
     * cảnh mã nguồn quanh cảnh báo.
     */
    public boolean authenticate(String username, String rawPassword) {
        List<User> matches = userRepository.findByUsername(username);
        if (matches.isEmpty()) {
            return false;
        }
        return passwordHasher.matches(rawPassword, matches.get(0).passwordHash());
    }

    /**
     * SEEDED — false positive: nuốt ngoại lệ, nhưng có chủ đích.
     *
     * <p>SpotBugs có thể báo DE_MIGHT_IGNORE. Ở đây việc bỏ qua ngoại lệ là đúng: hàm chỉ dùng để
     * hiển thị vai trò trên giao diện, và khi không tra được thì trả về vai trò mặc định là hành
     * vi mong muốn — không có lý do gì để làm hỏng cả trang chỉ vì không đọc được vai trò.
     */
    public String roleOrDefault(String username) {
        try {
            List<User> matches = userRepository.findByUsername(username);
            return matches.isEmpty() ? "GUEST" : matches.get(0).role();
        } catch (RuntimeException e) {
            return "GUEST";
        }
    }
}
