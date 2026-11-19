package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.hust.datn.evidence.domain.AgentModel;

import io.swagger.v3.oas.annotations.media.Schema;

/** Một câu hỏi gửi cho Agent trong một phiên đã mở. */
@Schema(description = "Câu hỏi gửi cho Agent")
public record AskRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 4000) String question,
        @Schema(
                        description = "Model trả lời câu này; bỏ trống = FREE. PREMIUM chỉ hợp lệ trong dự án Premium,"
                                + " nếu không backend trả 403")
                AgentModel model) {}
