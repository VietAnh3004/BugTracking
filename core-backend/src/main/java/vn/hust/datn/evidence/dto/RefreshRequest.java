package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshRequest(
        @NotBlank(message = "refreshToken không được để trống") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String refreshToken) {}
