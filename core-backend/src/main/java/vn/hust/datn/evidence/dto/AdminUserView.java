package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một dòng của trang quản trị. Cố ý KHÔNG có {@code passwordHash} hay {@code googleSubject}: chỉ
 * những cờ đủ để admin biết tài khoản đăng nhập được bằng cách nào.
 *
 * @param ownedProjectCount số dự án tài khoản SỞ HỮU — xoá tài khoản là xoá hết các dự án này
 * @param conversationCount số hội thoại với Agent do tài khoản tạo (ở mọi dự án)
 */
public record AdminUserView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emailVerified,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasPassword,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean googleLinked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ownedProjectCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int conversationCount) {}
