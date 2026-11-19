package vn.hust.datn.evidence.service.account;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.config.properties.AdminProperties;
import vn.hust.datn.evidence.domain.RefreshTokenRevokedReason;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.repository.RefreshTokenRepository;
import vn.hust.datn.evidence.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Đồng bộ tài khoản quản trị từ {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} mỗi lần khởi động.
 *
 * <p>Luôn đúng MỘT admin, và biến môi trường là nguồn sự thật duy nhất:
 * <ul>
 *   <li>chưa có hàng → tạo (đã xác thực email, vai trò ADMIN);
 *   <li>đổi mật khẩu trong {@code .env} → băm lại và thu hồi mọi phiên cũ của admin;
 *   <li>đổi tên đăng nhập → hàng ADMIN cũ bị hạ về USER (không còn quyền), tạo hàng mới;
 *   <li>để trống cả hai → mọi hàng ADMIN bị hạ về USER, tức TẮT trang quản trị.
 * </ul>
 *
 * <p>Không có đường nào khác để thành ADMIN: đăng ký thường và đăng nhập Google luôn tạo USER.
 *
 * <p>Không bao giờ log mật khẩu, kể cả ở mức DEBUG; log chỉ có id tài khoản.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_DISPLAY_NAME = "Quản trị viên";

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            demoteAllExcept(null);
            return;
        }

        User admin = userRepository.findByEmailIgnoreCase(properties.username()).orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setEmail(properties.username());
            admin.setDisplayName(ADMIN_DISPLAY_NAME);
            admin.setEnabled(true);
            admin.setCreatedAt(now());
        }
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        admin.setEmailVerifiedAt(admin.getEmailVerifiedAt() == null ? now() : admin.getEmailVerifiedAt());
        admin.setUpdatedAt(now());

        boolean passwordChanged = admin.getPasswordHash() == null
                || !passwordEncoder.matches(properties.password(), admin.getPasswordHash());
        if (passwordChanged) {
            admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        }
        admin = userRepository.saveAndFlush(admin);

        if (passwordChanged) {
            // Đổi mật khẩu trong .env mà phiên cũ vẫn sống thêm 14 ngày thì đổi mật khẩu để làm gì.
            refreshTokenRepository.revokeAllForUser(admin.getId(), RefreshTokenRevokedReason.PASSWORD_CHANGED);
        }
        demoteAllExcept(admin);
        log.info("Tài khoản quản trị đã đồng bộ từ cấu hình (id={})", admin.getId());
    }

    private void demoteAllExcept(User keep) {
        List<User> others = userRepository.findByRole(UserRole.ADMIN).stream()
                .filter(user -> keep == null || !user.getId().equals(keep.getId()))
                .toList();
        for (User other : others) {
            other.setRole(UserRole.USER);
            other.setUpdatedAt(now());
            userRepository.save(other);
            log.warn("Hạ vai trò ADMIN của tài khoản {} về USER: không còn khớp ADMIN_USERNAME", other.getId());
        }
    }

    private static OffsetDateTime now() {
        // Cùng quy ước với các nơi khác: UTC, cắt về micro-giây cho khớp timestamptz.
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
