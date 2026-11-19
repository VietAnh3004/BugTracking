package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.ProjectTier;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một dòng của trang quản trị dự án.
 *
 * @param ownerEmail email (hoặc tên đăng nhập) của chủ dự án
 */
public record AdminProjectView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectTier tier,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID ownerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ownerEmail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ownerDisplayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int memberCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int snapshotCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int conversationCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt) {}
