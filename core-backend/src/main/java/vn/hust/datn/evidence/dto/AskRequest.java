package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Một câu hỏi gửi cho Agent trong một phiên đã mở. */
@Schema(description = "Câu hỏi gửi cho Agent")
public record AskRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 4000) String question) {}
