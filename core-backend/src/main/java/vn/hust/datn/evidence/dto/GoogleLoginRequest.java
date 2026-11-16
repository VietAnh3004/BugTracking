package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** @param credential Google ID token mà nút "Đăng nhập bằng Google" trả về cho frontend */
public record GoogleLoginRequest(
        @NotBlank(message = "Thiếu thông tin đăng nhập Google") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String credential) {}
