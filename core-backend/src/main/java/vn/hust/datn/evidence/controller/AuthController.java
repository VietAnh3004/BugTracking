package vn.hust.datn.evidence.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.AuthConfigResponse;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.dto.ChangePasswordRequest;
import vn.hust.datn.evidence.dto.EmailRequest;
import vn.hust.datn.evidence.dto.GoogleLoginRequest;
import vn.hust.datn.evidence.dto.LoginRequest;
import vn.hust.datn.evidence.dto.RefreshRequest;
import vn.hust.datn.evidence.dto.RegisterRequest;
import vn.hust.datn.evidence.dto.RegisterResponse;
import vn.hust.datn.evidence.dto.ResetPasswordRequest;
import vn.hust.datn.evidence.dto.UpdateProfileRequest;
import vn.hust.datn.evidence.dto.UserResponse;
import vn.hust.datn.evidence.dto.VerifyEmailRequest;
import vn.hust.datn.evidence.service.AuthService;
import vn.hust.datn.evidence.service.RefreshTokenService;
import vn.hust.datn.evidence.service.account.AccountService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Đăng ký, đăng nhập, tài khoản của người đang đăng nhập. Endpoint công khai phải gắn
 * {@code @SecurityRequirements()} RỖNG để xoá yêu cầu {@code bearerAuth} toàn cục (khai ở
 * {@link vn.hust.datn.evidence.config.OpenApiConfig}) — VÀ phải nằm trong whitelist của
 * {@link vn.hust.datn.evidence.config.SecurityConfig#apiChain}. Hai danh sách đi cặp: thêm endpoint
 * công khai mà quên một bên là hoặc spec nói sai, hoặc endpoint trả 401.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;
    private final RefreshTokenService refreshTokenService;

    /** Tạo tài khoản chưa xác thực và gửi link xác thực. KHÔNG trả token. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    public AuthTokensResponse login(
            @Valid @RequestBody LoginRequest request, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @GetMapping("/config")
    @SecurityRequirements
    public AuthConfigResponse config() {
        return accountService.config();
    }

    /** Dùng link xác thực trong email; thành công thì đăng nhập luôn. */
    @PostMapping("/verify-email")
    @SecurityRequirements
    public AuthTokensResponse verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return accountService.verifyEmail(request.token(), userAgent);
    }

    /** Luôn 204, kể cả khi email không có tài khoản hay đã xác thực — chống dò tài khoản. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        accountService.resendVerification(request.email());
    }

    /** Luôn 204, kể cả khi email không có tài khoản — chống dò tài khoản. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    public void forgotPassword(@Valid @RequestBody EmailRequest request) {
        accountService.forgotPassword(request.email());
    }

    /** Đặt mật khẩu mới từ link trong email. Mọi phiên đang mở bị đăng xuất. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/google")
    @SecurityRequirements
    public AuthTokensResponse google(
            @Valid @RequestBody GoogleLoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return accountService.loginWithGoogle(request.credential(), userAgent);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(authService.me(currentUser.id()));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            @AuthenticationPrincipal CurrentUser currentUser, @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(currentUser.id(), request.displayName());
    }

    /**
     * Đổi mật khẩu (hoặc đặt lần đầu với tài khoản chỉ có Google). Mọi phiên cũ bị đóng; response
     * là phiên MỚI thay cho phiên đang dùng.
     */
    @PostMapping("/me/password")
    public AuthTokensResponse changePassword(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return accountService.changePassword(
                currentUser.id(), request.currentPassword(), request.newPassword(), userAgent);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public AuthTokensResponse refresh(
            @Valid @RequestBody RefreshRequest request, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return refreshTokenService.refresh(request.refreshToken(), userAgent);
    }

    // Không nhận @AuthenticationPrincipal: SecurityConfig.apiChain đã bắt buộc JWT hợp lệ cho
    // path này (không nằm trong whitelist), sở hữu refreshToken hợp lệ trong body là đủ để đăng
    // xuất - không cần đối chiếu với danh tính JWT.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.logout(request.refreshToken());
    }
}
