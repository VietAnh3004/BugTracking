package vn.hust.datn.evidence.controller;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.dto.tool.EvidenceSummaryItem;
import vn.hust.datn.evidence.dto.tool.GetCodeContextRequest;
import vn.hust.datn.evidence.dto.tool.GetCoverageRequest;
import vn.hust.datn.evidence.dto.tool.GetRelatedTestsRequest;
import vn.hust.datn.evidence.dto.tool.GetWarningRequest;
import vn.hust.datn.evidence.dto.tool.GetWarningResult;
import vn.hust.datn.evidence.dto.tool.ItemsResult;
import vn.hust.datn.evidence.dto.tool.SearchEvidenceRequest;
import vn.hust.datn.evidence.dto.tool.SearchEvidenceResult;
import vn.hust.datn.evidence.service.EvidenceQueryService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 5 evidence tool mà Agent Service gọi — RPC theo tên tool, không phải REST resource (ADR 0005).
 *
 * <p>Mỗi endpoint nhận ĐÚNG object arguments mà LLM sinh ra (snake_case, forward nguyên văn không
 * dịch tên trường) và trả về đúng shape tool, tách biệt hoàn toàn khỏi DTO của
 * {@link ProjectController}. Tool call = 1 POST là quan hệ 1:1 tuyệt đối, đo được qua {@link
 * vn.hust.datn.evidence.config.ToolCallLoggingInterceptor}.
 */
@RestController
@RequestMapping("/api/v1/tools")
@Tag(name = "evidence-tools")
@RequiredArgsConstructor
public class EvidenceToolController {

    private final EvidenceQueryService evidenceQueryService;

    @PostMapping("/get_warning")
    public GetWarningResult getWarning(@Valid @RequestBody GetWarningRequest request) {
        return GetWarningResult.from(evidenceQueryService.getWarning(request.analysisRunId(), request.evidenceId()));
    }

    @PostMapping("/get_code_context")
    public ItemsResult getCodeContext(@Valid @RequestBody GetCodeContextRequest request) {
        return ItemsResult.of(evidenceQueryService.getCodeContext(
                request.analysisRunId(), request.evidenceId(), request.contextLinesOrDefault()));
    }

    @PostMapping("/get_related_tests")
    public ItemsResult getRelatedTests(@Valid @RequestBody GetRelatedTestsRequest request) {
        var relations = request.relations() == null || request.relations().isEmpty() ? null : Set.copyOf(request.relations());
        return ItemsResult.of(evidenceQueryService.getRelatedTests(request.analysisRunId(), request.evidenceId(), relations));
    }

    @PostMapping("/get_coverage")
    public ItemsResult getCoverage(@Valid @RequestBody GetCoverageRequest request) {
        return ItemsResult.of(evidenceQueryService.getCoverage(request.analysisRunId(), request.evidenceId()));
    }

    @PostMapping("/search_evidence")
    public SearchEvidenceResult searchEvidence(@Valid @RequestBody SearchEvidenceRequest request) {
        Set<EvidenceType> types = request.types() == null || request.types().isEmpty() ? null : Set.copyOf(request.types());
        int limit = request.limitOrDefault();

        var records = evidenceQueryService.searchEvidence(request.analysisRunId(), request.query(), types, request.filePath(), limit);

        var items = records.stream()
                .map(e -> new EvidenceSummaryItem(e.getShortId(), e.getType(), e.getSummary(), e.getFilePath(), e.getStartLine(), e.getSymbol()))
                .toList();
        return new SearchEvidenceResult(items, items.size(), items.size() >= limit);
    }
}
