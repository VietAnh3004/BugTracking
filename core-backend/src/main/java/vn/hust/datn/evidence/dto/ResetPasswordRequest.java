package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu mã đặt lại mật khẩu") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @NewPassword @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, description = NewPassword.DESCRIPTION) String newPassword) {}
