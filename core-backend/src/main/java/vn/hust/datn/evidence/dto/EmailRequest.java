package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Thân request chỉ có email — gửi lại link xác thực, quên mật khẩu. */
public record EmailRequest(
        @NotBlank(message = "Email không được để trống") @Email(message = "Email không đúng định dạng") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String email) {}
