package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateProfileRequest(
        @NotBlank(message = "Tên hiển thị không được để trống") @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
