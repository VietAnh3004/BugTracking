package vn.hust.datn.evidence.config.security;

import java.util.UUID;

import vn.hust.datn.evidence.domain.UserRole;

/**
 * Danh tính người dùng đã xác thực, lấy trực tiếp từ claim của JWT — không truy vấn DB.
 *
 * <p>Bind vào tham số controller qua {@code @AuthenticationPrincipal CurrentUser user}. Service
 * layer chỉ nhận {@code UUID userId} thuần, không biết gì về Spring Security hay kiểu này — giữ
 * đúng phân tầng (§1bis kế hoạch).
 */
public record CurrentUser(UUID id, String email, UserRole role) {}
