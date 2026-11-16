package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.hust.datn.evidence.domain.GroundTruthVerdict;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Gán (hoặc sửa) nhãn ground truth cho một cảnh báo.
 *
 * <p>KHÔNG có trường {@code source}: nguồn do server suy từ vai trò (chủ dự án ⇒ MANUAL, thành viên
 * ⇒ BLIND_SECOND_RATER). Cho client tự khai nguồn là để thành viên ghi đè được nhãn của chủ dự án.
 *
 * <p>{@code rationale} bắt buộc — kế hoạch §5: viết được lý do nghĩa là đã đọc mã, không phải chép
 * kết luận của Agent.
 */
public record SaveGroundTruthRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull GroundTruthVerdict label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Lý do 1-2 câu, sau khi đọc mã")
                @NotBlank @Size(max = 2000) String rationale) {}
