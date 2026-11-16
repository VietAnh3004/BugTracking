package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.dto.LoginRequest;
import vn.hust.datn.evidence.dto.RegisterRequest;
import vn.hust.datn.evidence.dto.RegisterResponse;
import vn.hust.datn.evidence.dto.UserResponse;
import vn.hust.datn.evidence.exception.EmailAlreadyUsedException;
import vn.hust.datn.evidence.exception.EmailNotVerifiedException;
import vn.hust.datn.evidence.exception.InvalidCredentialsException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.mapper.UserMapper;
import vn.hust.datn.evidence.repository.UserRepository;
import vn.hust.datn.evidence.service.account.AccountEmails;

/**
 * Đăng ký, đăng nhập, {@code /auth/me}. Không có {@code @PreAuthorize}/tra cứu quyền sở hữu ở đây
 * — đó là {@code ProjectService} (B6); lớp này chỉ lo danh tính (ai đang gọi), không lo tài
 * nguyên nào thuộc về ai. Các luồng tài khoản còn lại (xác thực email, quên mật khẩu, Google, sửa
 * hồ sơ) ở {@link vn.hust.datn.evidence.service.account.AccountService}.
 */
@Service
public class AuthService {

    // Hash BCrypt hợp lệ của một chuỗi cố định KHÔNG PHẢI mật khẩu thật của ai — dùng để chạy
    // một lần so khớp giả khi không tìm thấy email, giữ thời gian phản hồi giống hệt trường hợp
    // "email tồn tại nhưng sai mật khẩu". Không làm việc này thì kẻ tấn công đo thời gian phản
    // hồi để dò xem một email có tồn tại hay không.
    private static final String DUMMY_HASH_FOR_TIMING = "$2a$04$C6UzMDM.H6dfI/f/IKcEeO0P/dU/4EF4wRcBz.uCbFvvV3T7YFdVi";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AccountEmails accountEmails;

    public AuthService(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AccountEmails accountEmails) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accountEmails = accountEmails;
    }

    /**
     * Tạo tài khoản CHƯA xác thực rồi gửi link xác thực. Không phát token: chưa chứng minh được
     * email là của người đăng ký thì chưa cho vào (quyết định của người dùng, V13).
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        OffsetDateTime now = now();
        User user = new User();
        user.setEmail(request.email().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setEmailVerifiedAt(null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.save(user);

        accountEmails.sendVerification(user);
        return new RegisterResponse(user.getEmail());
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        // Tài khoản chỉ có Google (không mật khẩu) đi CÙNG nhánh với "không có tài khoản": vẫn
        // chạy một lần so khớp giả để thời gian phản hồi không lộ ra loại tài khoản.
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH_FOR_TIMING);
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Kiểm SAU mật khẩu: chỉ người biết mật khẩu mới biết được "tài khoản này chưa xác thực".
        if (user.getEmailVerifiedAt() == null) {
            throw new EmailNotVerifiedException();
        }

        return issueTokens(user, userAgent);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userMapper.toResponse(requireUser(userId));
    }

    /** Một phiên đăng nhập MỚI (family refresh token mới) cho {@code user}. */
    @Transactional
    public AuthTokensResponse issueTokens(User user, String userAgent) {
        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueInitialRefreshToken(user.getId(), userAgent);
        return new AuthTokensResponse(
                accessToken, "Bearer", tokenService.accessTokenExpiresInSeconds(), refreshToken, userMapper.toResponse(user));
    }

    public User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("user", userId));
    }

    /** UTC + micro giây: đúng thứ Postgres giữ lại (CLAUDE.md, bẫy {@code OffsetDateTime.now()}). */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
