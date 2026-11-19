package vn.hust.datn.evidence.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.config.properties.AdminProperties;
import vn.hust.datn.evidence.domain.RefreshTokenRevokedReason;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.repository.RefreshTokenRepository;
import vn.hust.datn.evidence.repository.UserRepository;
import vn.hust.datn.evidence.service.TokenService;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Đồng bộ tài khoản admin từ biến môi trường. Mỗi test tự dựng một {@link AdminBootstrap} với
 * {@link AdminProperties} riêng (không dùng bean của context) để thử các cấu hình khác nhau mà không
 * phải khởi động lại Spring.
 */
class AdminBootstrapTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AdminProperties contextProperties;

    /**
     * Các test dưới đây đổi/tắt admin trong DB DÙNG CHUNG với mọi class test khác (một container cho cả bộ
     * test). Khôi phục admin theo cấu hình của context, nếu không AdminControllerTest chạy sau sẽ không
     * còn đăng nhập được bằng tài khoản admin của nó.
     */
    @AfterEach
    void khoiPhucAdminCuaContext() {
        chay(contextProperties.username(), contextProperties.password());
    }

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * Tự dựng bean nên KHÔNG đi qua proxy của Spring — phải tự bọc transaction, nếu không câu UPDATE
     * thu hồi refresh token báo "Executing an update/delete query" (cần transaction đang mở).
     */
    private void chay(String username, String password) {
        AdminBootstrap bootstrap = new AdminBootstrap(
                new AdminProperties(username, password), userRepository, refreshTokenRepository, passwordEncoder);
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(new DefaultApplicationArguments()));
    }

    @Test
    void taoAdminLanDauDaXacThucVaDangNhapDuocBangMatKhau() {
        String name = "admin-" + System.nanoTime();

        chay(name, "mat-khau-admin-1");

        User admin = userRepository.findByEmailIgnoreCase(name).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getEmailVerifiedAt()).isNotNull();
        assertThat(passwordEncoder.matches("mat-khau-admin-1", admin.getPasswordHash())).isTrue();
    }

    @Test
    void chayLaiKhongDoiGiThiKhongBamLaiMatKhau() {
        String name = "admin-" + System.nanoTime();
        chay(name, "mat-khau-admin-1");
        String hashTruoc = userRepository.findByEmailIgnoreCase(name).orElseThrow().getPasswordHash();

        chay(name, "mat-khau-admin-1");

        assertThat(userRepository.findByEmailIgnoreCase(name).orElseThrow().getPasswordHash()).isEqualTo(hashTruoc);
        assertThat(userRepository.findByRole(UserRole.ADMIN)).hasSize(1);
    }

    @Test
    void doiMatKhauTrongEnvThiBamLaiVaThuHoiMoiPhienCu() {
        String name = "admin-" + System.nanoTime();
        chay(name, "mat-khau-cu-1234");
        User admin = userRepository.findByEmailIgnoreCase(name).orElseThrow();
        tokenService.issueInitialRefreshToken(admin.getId(), "test");

        chay(name, "mat-khau-moi-1234");

        User sau = userRepository.findByEmailIgnoreCase(name).orElseThrow();
        assertThat(passwordEncoder.matches("mat-khau-moi-1234", sau.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("mat-khau-cu-1234", sau.getPasswordHash())).isFalse();
        assertThat(refreshTokenRepository.findAll())
                .filteredOn(t -> t.getUserId().equals(admin.getId()))
                .allSatisfy(t -> assertThat(t.getRevokedReason()).isEqualTo(RefreshTokenRevokedReason.PASSWORD_CHANGED));
    }

    @Test
    void doiTenDangNhapThiAdminCuBiHaVeUserVaChiConDungMotAdmin() {
        String cu = "admin-cu-" + System.nanoTime();
        String moi = "admin-moi-" + System.nanoTime();
        chay(cu, "mat-khau-admin-1");

        chay(moi, "mat-khau-admin-1");

        assertThat(userRepository.findByEmailIgnoreCase(cu).orElseThrow().getRole()).isEqualTo(UserRole.USER);
        assertThat(userRepository.findByEmailIgnoreCase(moi).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(userRepository.findByRole(UserRole.ADMIN)).hasSize(1);
    }

    @Test
    void deTrongCaHaiThiTatAdmin() {
        chay("admin-" + System.nanoTime(), "mat-khau-admin-1");

        chay("", "");

        assertThat(userRepository.findByRole(UserRole.ADMIN)).isEmpty();
    }

    @Test
    void tenAdminTrungEmailMotNguoiDungThuongThiNguoiDoThanhAdmin() {
        // Cố ý ghi lại hành vi này: ADMIN_USERNAME là nguồn sự thật, ai đang giữ tên đó là admin. Trong
        // thực tế không xảy ra (đăng ký đòi @Email, tên admin thì không phải email); test chỉ chốt rằng
        // bootstrap dùng lại hàng sẵn có thay vì tạo hàng thứ hai và vỡ UNIQUE lower(email).
        User thuong = fixtures.user("trung-ten-" + System.nanoTime() + "@x.test");

        chay(thuong.getEmail(), "mat-khau-admin-1");

        assertThat(userRepository.findByEmailIgnoreCase(thuong.getEmail()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void cauHinhDoDangHoacMatKhauNganThiNoNgayLucKhoiDong() {
        assertThatThrownBy(() -> new AdminProperties("admin", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CÙNG LÚC");
        assertThatThrownBy(() -> new AdminProperties("", "mat-khau-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminProperties("admin", "ngan"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tối thiểu 8");
    }

    @Test
    void matKhauKhongBaoGioLotVaoToString() {
        assertThat(new AdminProperties("admin", "bi-mat-khong-duoc-lo-1234").toString())
                .doesNotContain("bi-mat-khong-duoc-lo-1234");
    }
}
