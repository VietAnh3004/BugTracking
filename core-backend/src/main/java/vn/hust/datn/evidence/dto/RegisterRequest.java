package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @NotBlank(message = "Email không được để trống") @Email(message = "Email không đúng định dạng") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String email,
        // Quy tắc mật khẩu (độ dài, chữ cái, chữ số) và lý do không giới hạn tối đa: xem NewPassword.
        @NewPassword @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, description = NewPassword.DESCRIPTION) String password,
        @NotBlank(message = "Tên hiển thị không được để trống") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
