package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @NotBlank(message = "Email không được để trống") @Email(message = "Email không đúng định dạng") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String email,
        // Không giới hạn ký tự tối đa: BCrypt tự cắt ở 72 byte, không cần chặn phía ta; giới hạn
        // tối thiểu chống mật khẩu quá yếu ngay từ input, không đợi tới lúc hash rồi mới biết.
        @NotBlank(message = "Mật khẩu không được để trống") @Size(min = 8, message = "Mật khẩu phải dài tối thiểu 8 ký tự") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String password,
        @NotBlank(message = "Tên hiển thị không được để trống") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
