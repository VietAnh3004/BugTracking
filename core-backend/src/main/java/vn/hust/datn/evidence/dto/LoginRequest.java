package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @NotBlank(message = "Email không được để trống") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @NotBlank(message = "Mật khẩu không được để trống") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String password) {}
