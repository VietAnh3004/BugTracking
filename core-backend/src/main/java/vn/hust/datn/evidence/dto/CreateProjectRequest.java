package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateProjectRequest(
        @NotBlank(message = "Tên dự án không được để trống") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String description) {}
