package vn.hust.datn.casestudy.vulnshop.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hust.datn.casestudy.vulnshop.util.AuditLogger;

/** Điểm vào HTTP cho đăng nhập. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditLogger auditLogger;

    public AuthController(AuthService authService, AuditLogger auditLogger) {
        this.authService = authService;
        this.auditLogger = auditLogger;
    }

    /**
     * Đăng nhập.
     *
     * <p>{@code username} từ đây đi thẳng xuống {@code UserRepository.findByUsername} và bị nối
     * vào câu lệnh SQL — đây là điểm khởi đầu của đường đi dữ liệu bị nhiễm.
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        boolean ok = authService.authenticate(username, password);
        if (ok) {
            auditLogger.logLogin(username, "0.0.0.0");
        }
        return ok ? "OK" : "FAILED";
    }

    /**
     * SEEDED — lỗi thật: chuyển hướng không kiểm tra đích đến.
     *
     * <p>{@code next} do người dùng cung cấp và được đưa thẳng vào {@code sendRedirect}. Kẻ tấn
     * công gửi liên kết trỏ tới trang đăng nhập của chính hệ thống kèm {@code next=https://ke-gia-mao}
     * để lừa người dùng sang trang giả mạo mà vẫn thấy tên miền quen thuộc ở bước đầu.
     *
     * <p>Kỳ vọng Semgrep bắt (unvalidated-redirect).
     */
    @GetMapping("/login-redirect")
    public void loginRedirect(@RequestParam String next, HttpServletResponse response) throws IOException {
        response.sendRedirect(next);
    }
}
