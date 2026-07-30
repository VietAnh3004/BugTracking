package vn.hust.datn.evidence.dto.tool;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Mọi tool nhận {@code analysisRunId} không ngoại lệ — short_id chỉ unique trong phạm vi một run
 * (UNIQUE(analysis_run_id, short_id)), không có run id thì lookup vô nghĩa.
 *
 * <p>{@code evidenceId} nhận short_id dạng {@code EV-XXXX}, KHÔNG nhận UUID — LLM chỉ biết short
 * id (ADR 0004 §4). Không chấp nhận cả hai kiểu để không làm hỏng số liệu Evidence Citation
 * Accuracy.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetWarningRequest(@NotNull UUID analysisRunId, @NotBlank String evidenceId) {}
