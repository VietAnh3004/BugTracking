package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.dto.LoginRequest;
import vn.hust.datn.evidence.dto.RegisterRequest;
import vn.hust.datn.evidence.dto.UserResponse;
import vn.hust.datn.evidence.exception.EmailAlreadyUsedException;
import vn.hust.datn.evidence.exception.InvalidCredentialsException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.mapper.UserMapper;
import vn.hust.datn.evidence.repository.UserRepository;

/**
 * Đăng ký, đăng nhập, {@code /auth/me}. Không có {@code @PreAuthorize}/tra cứu quyền sở hữu ở đây
 * — đó là {@code ProjectService} (B6); lớp này chỉ lo danh tính (ai đang gọi), không lo tài
 * nguyên nào thuộc về ai.
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

    public AuthService(
            UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthTokensResponse register(RegisterRequest request, String userAgent) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.save(user);

        return issueTokens(user, userAgent);
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH_FOR_TIMING);
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user, userAgent);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("user", userId));
        return userMapper.toResponse(user);
    }

    private AuthTokensResponse issueTokens(User user, String userAgent) {
        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueInitialRefreshToken(user.getId(), userAgent);
        return new AuthTokensResponse(
                accessToken, "Bearer", tokenService.accessTokenExpiresInSeconds(), refreshToken, userMapper.toResponse(user));
    }
}
