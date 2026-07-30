package vn.hust.datn.evidence.dto.tool;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SearchEvidenceResult(List<EvidenceSummaryItem> items, int total, boolean truncated) {}
