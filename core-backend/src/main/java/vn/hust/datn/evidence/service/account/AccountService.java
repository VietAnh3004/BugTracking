package vn.hust.datn.evidence.service.account;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.config.properties.AccountProperties;
import vn.hust.datn.evidence.domain.AccountTokenPurpose;
import vn.hust.datn.evidence.domain.RefreshTokenRevokedReason;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.dto.AuthConfigResponse;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.dto.UserResponse;
import vn.hust.datn.evidence.exception.GoogleAccountConflictException;
import vn.hust.datn.evidence.exception.GoogleLoginDisabledException;
import vn.hust.datn.evidence.exception.InvalidGoogleTokenException;
import vn.hust.datn.evidence.exception.WrongCurrentPasswordException;
import vn.hust.datn.evidence.mapper.UserMapper;
import vn.hust.datn.evidence.repository.RefreshTokenRepository;
import vn.hust.datn.evidence.repository.UserRepository;
import vn.hust.datn.evidence.service.AuthService;

/**
 * Các luồng tài khoản ngoài đăng ký/đăng nhập: xác thực email, quên mật khẩu, đăng nhập Google,
 * sửa hồ sơ, đổi mật khẩu.
 *
 * <p><b>Hai endpoint nhận email từ người lạ ({@link #resendVerification}, {@link #forgotPassword})
 * LUÔN trả về như nhau</b> dù email có tài khoản hay không — nếu không, chúng thành công cụ dò xem
 * ai có tài khoản. Thư được gửi trên luồng riêng ({@link MailDispatcher}) nên thời gian phản hồi
 * cũng không lộ.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AccountTokenService tokens;
    private final AccountEmails emails;
    private final GoogleIdTokenVerifier googleVerifier;
    private final AccountProperties properties;

    public AccountService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            AccountTokenService tokens,
            AccountEmails emails,
            GoogleIdTokenVerifier googleVerifier,
            AccountProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.tokens = tokens;
        this.emails = emails;
        this.googleVerifier = googleVerifier;
        this.properties = properties;
    }

    public AuthConfigResponse config() {
        return new AuthConfigResponse(properties.googleEnabled() ? properties.googleClientId() : null);
    }

    // ===================================== xác thực email =====================================

    /** Bấm link trong email: xác thực xong là ĐĂNG NHẬP LUÔN — người dùng vừa chứng minh hộp thư. */
    @Transactional
    public AuthTokensResponse verifyEmail(String token, String userAgent) {
        User user = authService.requireUser(tokens.consume(token, AccountTokenPurpose.VERIFY_EMAIL));
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(AuthService.now());
            user.setUpdatedAt(AuthService.now());
        }
        return authService.issueTokens(user, userAgent);
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository
                .findByEmailIgnoreCase(email)
                .filter(user -> user.getEmailVerifiedAt() == null)
                .ifPresent(emails::sendVerification);
    }

    // ===================================== quên mật khẩu ======================================

    /** Cả tài khoản chỉ có Google cũng nhận được thư: đây là cách họ ĐẶT mật khẩu lần đầu. */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).filter(User::isEnabled).ifPresent(emails::sendPasswordReset);
    }

    /**
     * Đặt mật khẩu mới từ link trong email. Cũng đánh dấu email ĐÃ xác thực: mở được link trong hộp
     * thư là bằng chứng mạnh như link xác thực.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = authService.requireUser(tokens.consume(token, AccountTokenPurpose.RESET_PASSWORD));
        applyNewPassword(user, newPassword);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(AuthService.now());
        }
    }

    // ===================================== hồ sơ =====================================

    @Transactional
    public UserResponse updateProfile(UUID userId, String displayName) {
        User user = authService.requireUser(userId);
        user.setDisplayName(displayName.trim());
        user.setUpdatedAt(AuthService.now());
        return userMapper.toResponse(user);
    }

    /**
     * Đổi (hoặc đặt lần đầu, với tài khoản chỉ có Google) mật khẩu. Mọi phiên cũ bị đóng — kể cả
     * phiên đang gọi — rồi trả về một phiên MỚI để người đang thao tác không bị văng ra.
     */
    @Transactional
    public AuthTokensResponse changePassword(UUID userId, String currentPassword, String newPassword, String userAgent) {
        User user = authService.requireUser(userId);
        if (user.getPasswordHash() != null
                && (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new WrongCurrentPasswordException();
        }
        applyNewPassword(user, newPassword);
        return authService.issueTokens(user, userAgent);
    }

    private void applyNewPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(AuthService.now());
        tokens.invalidate(user.getId(), AccountTokenPurpose.RESET_PASSWORD); // link đặt lại còn treo
        refreshTokenRepository.revokeAllForUser(user.getId(), RefreshTokenRevokedReason.PASSWORD_CHANGED);
        emails.sendPasswordChanged(user);
    }

    // ===================================== Google =====================================

    /**
     * Đăng nhập bằng Google. Thứ tự tra cứu: theo {@code sub} trước (đã liên kết), rồi mới theo
     * email (liên kết tự động — quyết định của người dùng), cuối cùng tạo tài khoản mới.
     *
     * <p><b>Chống chiếm tài khoản đặt trước (pre-account-takeover):</b> kẻ xấu đăng ký trước bằng
     * email của nạn nhân với mật khẩu của HẮN, không xác thực được (không vào được hộp thư), rồi chờ.
     * Nạn nhân đăng nhập Google, hệ thống liên kết vào tài khoản đó — và kẻ xấu vẫn đăng nhập được
     * bằng mật khẩu hắn đặt. Vì vậy: liên kết vào tài khoản CHƯA xác thực thì XOÁ mật khẩu cũ và
     * đóng mọi phiên của nó.
     */
    @Transactional
    public AuthTokensResponse loginWithGoogle(String credential, String userAgent) {
        if (!properties.googleEnabled()) {
            throw new GoogleLoginDisabledException();
        }
        GoogleIdentity identity = googleVerifier.verify(credential);
        if (!identity.emailVerified()) {
            throw new InvalidGoogleTokenException();
        }

        Optional<User> bySubject = userRepository.findByGoogleSubject(identity.subject());
        if (bySubject.isPresent()) {
            User user = bySubject.get();
            refreshPicture(user, identity);
            return authService.issueTokens(requireEnabled(user), userAgent);
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(identity.email());
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            if (user.getGoogleSubject() != null) {
                throw new GoogleAccountConflictException();
            }
            // Gán sub TRƯỚC khi xoá mật khẩu: câu UPDATE hàng loạt bên dưới có thể khiến Hibernate
            // flush app_user giữa chừng, và một hàng vừa mất mật khẩu vừa chưa có Google vi phạm
            // ck_app_user_can_sign_in.
            user.setGoogleSubject(identity.subject());
            if (user.getEmailVerifiedAt() == null) {
                log.warn("Lien ket Google vao tai khoan CHUA xac thuc {} - xoa mat khau cu", user.getId());
                user.setPasswordHash(null);
                user.setEmailVerifiedAt(AuthService.now());
                refreshTokenRepository.revokeAllForUser(user.getId(), RefreshTokenRevokedReason.PASSWORD_CHANGED);
            }
            refreshPicture(user, identity);
            return authService.issueTokens(requireEnabled(user), userAgent);
        }

        User user = new User();
        user.setEmail(identity.email());
        user.setDisplayName(displayNameOf(identity));
        user.setPasswordHash(null);
        user.setGoogleSubject(identity.subject());
        user.setGooglePictureUrl(blankToNull(identity.pictureUrl()));
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setEmailVerifiedAt(AuthService.now());
        user.setCreatedAt(AuthService.now());
        user.setUpdatedAt(AuthService.now());
        return authService.issueTokens(userRepository.save(user), userAgent);
    }

    private static User requireEnabled(User user) {
        if (!user.isEnabled()) {
            throw new InvalidGoogleTokenException();
        }
        return user;
    }

    /** Ảnh Google đổi được bất cứ lúc nào — cập nhật mỗi lần đăng nhập. */
    private static void refreshPicture(User user, GoogleIdentity identity) {
        user.setGooglePictureUrl(blankToNull(identity.pictureUrl()));
        user.setUpdatedAt(AuthService.now());
    }

    private static String displayNameOf(GoogleIdentity identity) {
        if (identity.name() != null && !identity.name().isBlank()) {
            String name = identity.name().trim();
            return name.length() > 100 ? name.substring(0, 100) : name;
        }
        String email = identity.email();
        int at = email.indexOf('@');
        return (at > 0 ? email.substring(0, at) : email).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
