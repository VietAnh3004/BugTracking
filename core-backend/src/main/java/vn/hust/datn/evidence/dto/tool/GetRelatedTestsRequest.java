package vn.hust.datn.evidence.dto.tool;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import vn.hust.datn.evidence.domain.EvidenceLinkRelation;

/** {@code relations} là enum trực tiếp: giá trị không hợp lệ tự 400 qua handler JSON-not-readable. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetRelatedTestsRequest(
        @NotNull UUID analysisRunId, @NotBlank String evidenceId, List<EvidenceLinkRelation> relations) {}
