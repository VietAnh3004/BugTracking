package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param currentPassword bắt buộc khi tài khoản ĐÃ có mật khẩu; tài khoản chỉ có Google thì bỏ
 *     trống — đó là lần ĐẶT mật khẩu đầu tiên
 */
public record ChangePasswordRequest(
        String currentPassword,
        @NotBlank(message = "Mật khẩu mới không được để trống") @Size(min = 8, message = "Mật khẩu phải dài tối thiểu 8 ký tự") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String newPassword) {}
