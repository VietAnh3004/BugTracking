package vn.hust.datn.evidence.dto.tool;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetCodeContextRequest(
        @NotNull UUID analysisRunId, @NotBlank String evidenceId, @Min(0) @Max(50) Integer contextLines) {

    public int contextLinesOrDefault() {
        return contextLines == null ? 5 : contextLines;
    }
}
