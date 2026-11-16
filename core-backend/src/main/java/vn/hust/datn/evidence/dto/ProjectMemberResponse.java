package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.service.ProjectService;

import io.swagger.v3.oas.annotations.media.Schema;

/** Một thành viên của dự án, như màn hình quản lý thành viên cần. */
public record ProjectMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime addedAt) {

    public static ProjectMemberResponse from(ProjectService.MemberView view) {
        return new ProjectMemberResponse(view.userId(), view.email(), view.displayName(), view.addedAt());
    }
}
