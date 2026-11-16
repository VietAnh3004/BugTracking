package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.ProjectRole;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Không bao giờ trả entity JPA ra API — DTO record riêng cho mọi response (§1bis.2 kế hoạch).
 *
 * <p>{@code @Schema(requiredMode = REQUIRED)} tường minh trên từng thành phần: springdoc không tự
 * suy ra "required" cho record từ Bean Validation, nên nếu bỏ trống thì mọi field sinh ra ở FE là
 * optional ({@code string | undefined}) dù thực tế luôn có giá trị.
 */
public record ProjectResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Vai trò của NGƯỜI ĐANG GỌI với dự án này: OWNER (chủ) hoặc MEMBER (được chia sẻ)")
                ProjectRole role) {}
