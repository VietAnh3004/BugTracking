package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một thành viên của dự án, như màn hình quản lý thành viên cần: ai, email gì, được mời lúc nào.
 *
 * <p>Ghép từ hai entity ({@code User} + {@code ProjectMember}) nên {@code ProjectService} dựng
 * thẳng record này — không qua MapStruct, cũng không qua một record trung gian ở tầng service.
 */
public record ProjectMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime addedAt) {}
