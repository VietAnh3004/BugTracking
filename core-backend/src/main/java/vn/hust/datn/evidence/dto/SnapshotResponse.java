package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import vn.hust.datn.evidence.domain.SnapshotSourceType;
import vn.hust.datn.evidence.domain.SnapshotStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record SnapshotResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID projectId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SnapshotStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SnapshotSourceType sourceType,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String sourceRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fileCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalSizeBytes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String errorMessage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt) {}
