package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Đổi tên một phiên hỏi đáp. */
@Schema(description = "Tên mới của phiên hỏi đáp")
public record RenameConversationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String title) {}
