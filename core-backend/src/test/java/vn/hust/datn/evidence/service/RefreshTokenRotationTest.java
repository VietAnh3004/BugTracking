package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.RefreshToken;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.dto.RegisterRequest;
import vn.hust.datn.evidence.exception.InvalidRefreshTokenException;
import vn.hust.datn.evidence.exception.RefreshTokenReuseException;
import vn.hust.datn.evidence.repository.RefreshTokenRepository;

/**
 * Xoay vòng refresh token là bề mặt bảo mật nhạy cảm nhất của tuần 5-7 — test kỹ theo §1bis.7.
 * Không gọi qua HTTP: {@link AuthService}/{@link RefreshTokenService} là service thuần, test trực
 * tiếp để khẳng định TÍNH CHẤT bảo mật (token trong DB là hash, cả family bị thu hồi khi tái sử
 * dụng), không chỉ hành vi HTTP bề mặt.
 */
class RefreshTokenRotationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void refreshTraVeCapTokenMoiVaTokenCuHetHieuLuc() {
        AuthTokensResponse initial = dangKyNguoiDungMoi();

        AuthTokensResponse refreshed = refreshTokenService.refresh(initial.refreshToken(), "junit-test");

        // KHÔNG so sánh accessToken cũ/mới: hai JWT phát trong cùng một giây có claim iat/exp
        // giống hệt nhau nên byte-for-byte giống nhau - đó là hành vi đúng của JWT (stateless,
        // không tracking theo lần phát), không phải dấu hiệu "chưa cấp token mới". Chỉ
        // refreshToken (opaque, ngẫu nhiên) mới đảm bảo khác nhau giữa hai lần phát.
        assertThat(refreshed.refreshToken()).isNotEqualTo(initial.refreshToken());

        // Token cũ đã dùng - refresh lại lần nữa với token CŨ phải bị coi là tái sử dụng.
        assertThatThrownBy(() -> refreshTokenService.refresh(initial.refreshToken(), "junit-test"))
                .isInstanceOf(RefreshTokenReuseException.class);
    }

    @Test
    void dungLaiRefreshTokenDaXoayThiCaHoBiThuHoiKeCaTokenMoiNhat() {
        AuthTokensResponse initial = dangKyNguoiDungMoi();
        AuthTokensResponse refreshed = refreshTokenService.refresh(initial.refreshToken(), "junit-test");

        // Dùng lại token ĐẦU TIÊN (đã xoay) - đây là dấu hiệu bị lộ hoặc client hỏng.
        assertThatThrownBy(() -> refreshTokenService.refresh(initial.refreshToken(), "junit-test"))
                .isInstanceOf(RefreshTokenReuseException.class);

        // Hệ quả: token MỚI NHẤT (vốn hợp lệ) cũng bị thu hồi theo - đây là bản chất của reuse
        // detection, không phải chỉ token cũ bị chặn. Ném RefreshTokenReuseException (không phải
        // InvalidRefreshTokenException) vì nhánh "revoked_at != null" bắt trước nhánh hết hạn.
        assertThatThrownBy(() -> refreshTokenService.refresh(refreshed.refreshToken(), "junit-test"))
                .isInstanceOf(RefreshTokenReuseException.class);
    }

    @Test
    void refreshTokenHetHanTraVe401() {
        AuthTokensResponse initial = dangKyNguoiDungMoi();

        // Chỉnh thẳng DB để mô phỏng token đã hết hạn - không đợi thời gian thật trôi qua. Nhắm
        // đúng hàng của token đang test bằng token_hash - KHÔNG dùng "LIMIT 1" bừa bãi, vì các
        // test khác trong cùng lớp (và cả lớp khác chạy trước) đã tạo sẵn nhiều hàng refresh_token
        // (không có @Transactional rollback ở AbstractIntegrationTest).
        String tokenHash = tokenService.hashRefreshToken(initial.refreshToken());
        int rowsUpdated = jdbcTemplate.update(
                "UPDATE refresh_token SET expires_at = ? WHERE token_hash = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                tokenHash);
        assertThat(rowsUpdated).isEqualTo(1);

        assertThatThrownBy(() -> refreshTokenService.refresh(initial.refreshToken(), "junit-test"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutLamRefreshTokenKhongDungDuocNua() {
        AuthTokensResponse initial = dangKyNguoiDungMoi();

        refreshTokenService.logout(initial.refreshToken());

        assertThatThrownBy(() -> refreshTokenService.refresh(initial.refreshToken(), "junit-test"))
                .isInstanceOf(RefreshTokenReuseException.class);
    }

    @Test
    void refreshTokenTrongDbLaHashChuKhongPhaiChuoiGoc() {
        AuthTokensResponse initial = dangKyNguoiDungMoi();

        List<RefreshToken> rows = refreshTokenRepository.findAll();
        assertThat(rows).isNotEmpty();
        assertThat(rows).noneMatch(row -> row.getTokenHash().equals(initial.refreshToken()));
        // SHA-256 hex luôn dài 64 ký tự - dấu hiệu gián tiếp đây thực sự là hash, không phải
        // chuỗi gốc bị chép nhầm cột.
        assertThat(rows).allMatch(row -> row.getTokenHash().length() == 64);
    }

    private AuthTokensResponse dangKyNguoiDungMoi() {
        String email = "refresh-%s@test.local".formatted(UUID.randomUUID());
        return authService.register(new RegisterRequest(email, "mat-khau-du-dai-8", "Nguoi Dung Test"), "junit-test");
    }
}
