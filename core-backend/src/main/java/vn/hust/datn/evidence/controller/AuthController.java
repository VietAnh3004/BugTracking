package vn.hust.datn.evidence.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.AuthTokensResponse;
import vn.hust.datn.evidence.dto.LoginRequest;
import vn.hust.datn.evidence.dto.RefreshRequest;
import vn.hust.datn.evidence.dto.RegisterRequest;
import vn.hust.datn.evidence.dto.UserResponse;
import vn.hust.datn.evidence.service.AuthService;
import vn.hust.datn.evidence.service.RefreshTokenService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Đăng ký, đăng nhập, thông tin người dùng hiện tại. {@code register}/{@code login} phải gắn
 * {@code @SecurityRequirements()} RỖNG để xoá yêu cầu {@code bearerAuth} toàn cục (khai ở
 * {@link vn.hust.datn.evidence.config.OpenApiConfig}) — khớp với whitelist trong
 * {@link vn.hust.datn.evidence.config.SecurityConfig#apiChain}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    public AuthTokensResponse register(
            @Valid @RequestBody RegisterRequest request, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.register(request, userAgent);
    }

    @PostMapping("/login")
    @SecurityRequirements
    public AuthTokensResponse login(
            @Valid @RequestBody LoginRequest request, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(authService.me(currentUser.id()));
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
