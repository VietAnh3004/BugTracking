package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.ArtifactSource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một lần chạy phân tích, cho UI.
 *
 * <p>{@code errorMessage} và hai bộ đếm đường dẫn CÓ trong DTO một cách có chủ đích: FE phải hiển
 * thị được vì sao một run hỏng, và tỉ lệ vị trí không đối chiếu được là chỉ số sức khoẻ mà ADR
 * 0004 yêu cầu phải nhìn thấy — giấu nó đi thì thất bại chuẩn hoá đường dẫn lại trở về thành thứ
 * hỏng trong im lặng.
 */
public record AnalysisRunResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID snapshotId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AnalysisRunStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Chỉ đếm STATIC_WARNING, không phải tổng số evidence")
                int findingCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int locationCount,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Số vị trí không đối chiếu được với snapshot_file.path; mẫu số là locationCount")
                int unresolvedLocationCount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Bắt buộc khi status=SUCCEEDED_CLEAN")
                String cleanAckNote,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String errorMessage,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Phiên bản đọc từ chính artifact; khoá 'jacoco' vắng mặt là bình thường")
                Map<String, Object> toolVersions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Số evidence_link EvidenceLinker sinh ra")
                int linkCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Số evidence CODE_CONTEXT do JavaParser dựng")
                int codeContextCount,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Số cảnh báo không nhận được liên kết nào; mẫu số là findingCount")
                int unlinkedWarningCount,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "Phân bố liên kết theo EvidenceLinkMethod — NAMING_CONVENTION là mức yếu nhất")
                Map<String, Object> linkCountsByMethod,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Artifact do người dùng nạp tay hay do sandbox tự chạy — hai mức đáng tin khác nhau")
                ArtifactSource artifactSource,
        @Schema(
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                        description = "NULL với run nạp tay: ở đó không có canary để kiểm, khác hẳn false")
                Boolean canaryOk,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String analyzerImageDigest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime startedAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) OffsetDateTime finishedAt) {}
