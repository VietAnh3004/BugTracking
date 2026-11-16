package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyEmailRequest(
        @NotBlank(message = "Thiếu mã xác thực") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token) {}
