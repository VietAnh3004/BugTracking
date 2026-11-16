package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Mời một người vào dự án bằng email tài khoản của họ. */
public record AddProjectMemberRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Email người đó đã dùng để đăng ký")
                @NotBlank @Email String email) {}
