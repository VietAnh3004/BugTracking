package vn.hust.datn.evidence.dto.tool;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import vn.hust.datn.evidence.domain.EvidenceType;

/** Item của search_evidence — không có relation/method vì không có evidence "gốc" để so sánh. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvidenceSummaryItem(
        String shortId, EvidenceType type, String summary, String filePath, Integer startLine, String symbol) {}
