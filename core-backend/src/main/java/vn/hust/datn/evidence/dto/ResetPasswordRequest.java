package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu mã đặt lại mật khẩu") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @NotBlank(message = "Mật khẩu không được để trống") @Size(min = 8, message = "Mật khẩu phải dài tối thiểu 8 ký tự") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String newPassword) {}
