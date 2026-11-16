package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param hasPassword {@code false} với tài khoản chỉ đăng nhập bằng Google — trang tài khoản hiện
 *     "Đặt mật khẩu" thay vì "Đổi mật khẩu" (không có mật khẩu hiện tại để hỏi)
 * @param avatarUrl ảnh Google nếu đã liên kết, không thì Gravatar theo email. Gravatar trả 404 khi
 *     người dùng chưa đăng ký ảnh ở đó — frontend bắt lỗi tải ảnh và hiện chữ cái đầu của tên.
 */
public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emailVerified,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasPassword,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean googleLinked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String avatarUrl) {}
