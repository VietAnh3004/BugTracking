package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.RefreshToken;
import vn.hust.datn.evidence.domain.RefreshTokenRevokedReason;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.exception.InvalidRefreshTokenException;
import vn.hust.datn.evidence.exception.RefreshTokenReuseException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.mapper.UserMapper;
import vn.hust.datn.evidence.repository.RefreshTokenRepository;
import vn.hust.datn.evidence.repository.UserRepository;

/**
 * Trạng thái xoay vòng/thu hồi của refresh token — "cách mint token" nằm ở {@link TokenService}.
 *
 * <p>Refresh token là opaque, lưu hash. Mỗi lần {@code /auth/refresh} thành công: token cũ được
 * đánh dấu {@code used_at} + thu hồi lý do {@code ROTATED}, một token mới cùng {@code family_id}
 * được phát ra. Nếu một token đã xoay còn bị dùng lại (đã có {@code used_at}/{@code revoked_at})
 * — dấu hiệu bị lộ hoặc client hỏng — <b>toàn bộ family</b> bị thu hồi, không chỉ token đó.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final TokenService tokenService;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            UserMapper userMapper,
            TokenService tokenService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.tokenService = tokenService;
    }

    /**
     * {@code noRollbackFor} bắt buộc: nhánh tái sử dụng/hết hạn GHI (thu hồi cả family) rồi MỚI
     * ném exception để báo 401 cho caller. Mặc định Spring rollback toàn bộ transaction khi một
     * unchecked exception thoát ra khỏi method {@code @Transactional} — nếu không khai
     * {@code noRollbackFor}, chính thao tác thu hồi (biện pháp bảo mật quan trọng nhất của class
     * này) sẽ bị cuốn theo rollback và KHÔNG BAO GIỜ thực sự lưu xuống DB, dù test/log tưởng như
     * đã chạy đúng. Bug này bắt được bằng {@code RefreshTokenRotationTest} khi viết test đầu tiên.
     */
    @Transactional(noRollbackFor = {RefreshTokenReuseException.class, InvalidRefreshTokenException.class})
    public AuthTokensResponse refresh(String plaintextToken, String userAgent) {
        String hash = tokenService.hashRefreshToken(plaintextToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);

        if (token == null) {
            throw new InvalidRefreshTokenException("không tìm thấy");
        }
        if (token.getRevokedAt() != null || token.getUsedAt() != null) {
            revokeFamilyAndLog(token, RefreshTokenRevokedReason.REUSE_DETECTED);
            throw new RefreshTokenReuseException();
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            revokeFamilyAndLog(token, RefreshTokenRevokedReason.EXPIRED);
            throw new InvalidRefreshTokenException("đã hết hạn");
        }

        // Điều kiện `used_at IS NULL` trong câu UPDATE (xem RefreshTokenRepository.markRotated)
        // là phần quan trọng nhất: nó biến kiểm-tra-rồi-cập-nhật thành một thao tác nguyên tử của
        // DB. Nếu 0 hàng bị ảnh hưởng, một request refresh khác đã xoay ĐÚNG token này trước ta
        // trong khoảng thời gian giữa lúc đọc và lúc ghi - xử lý y hệt nhánh tái sử dụng.
        int rowsUpdated = refreshTokenRepository.markRotated(token.getId());
        if (rowsUpdated == 0) {
            revokeFamilyAndLog(token, RefreshTokenRevokedReason.REUSE_DETECTED);
            throw new RefreshTokenReuseException();
        }

        User user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("user", token.getUserId()));

        String newAccessToken = tokenService.issueAccessToken(user);
        String newRefreshToken = tokenService.issueRefreshToken(user.getId(), token.getFamilyId(), userAgent);

        return new AuthTokensResponse(
                newAccessToken,
                "Bearer",
                tokenService.accessTokenExpiresInSeconds(),
                newRefreshToken,
                userMapper.toResponse(user));
    }

    /** Đăng xuất: thu hồi cả family chứa token này. Im lặng (không ném lỗi) nếu token không tồn tại — logout là thao tác dọn dẹp, không phải oracle để dò token hợp lệ. */
    @Transactional
    public void logout(String plaintextToken) {
        String hash = tokenService.hashRefreshToken(plaintextToken);
        refreshTokenRepository
                .findByTokenHash(hash)
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), RefreshTokenRevokedReason.LOGOUT));
    }

    private void revokeFamilyAndLog(RefreshToken token, RefreshTokenRevokedReason reason) {
        refreshTokenRepository.revokeFamily(token.getFamilyId(), reason);
        log.warn(
                "Thu hoi refresh token family={} userId={} ly do={}", token.getFamilyId(), token.getUserId(), reason);
    }
}
