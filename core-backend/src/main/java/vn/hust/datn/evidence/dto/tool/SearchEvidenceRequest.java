package vn.hust.datn.evidence.dto.tool;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import vn.hust.datn.evidence.domain.EvidenceType;

/** Tuần 4 chỉ ILIKE — không BM25, không vector (ADR 0004 §7 cố ý chưa chốt số chiều embedding). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SearchEvidenceRequest(
        @NotNull UUID analysisRunId,
        @NotBlank String query,
        List<EvidenceType> types,
        String filePath,
        @Min(1) @Max(50) Integer limit) {

    public int limitOrDefault() {
        return limit == null ? 20 : limit;
    }
}
