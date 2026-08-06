package vn.hust.datn.evidence.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.TestCase;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningLocation;
import vn.hust.datn.evidence.dto.EvidenceDetailResponse;
import vn.hust.datn.evidence.dto.RelatedEvidenceView;
import vn.hust.datn.evidence.dto.TestCaseSummaryView;
import vn.hust.datn.evidence.dto.WarningDetailView;
import vn.hust.datn.evidence.dto.WarningLocationView;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;
import vn.hust.datn.evidence.repository.TestCaseRepository;
import vn.hust.datn.evidence.repository.WarningLocationRepository;
import vn.hust.datn.evidence.repository.WarningRepository;

/**
 * Ráp dữ liệu cho màn hình Evidence Explorer.
 *
 * <p>Không tự viết truy vấn liên kết — gọi {@link EvidenceQueryService#relatedPairs} dùng chung với
 * đường tool, đúng ADR 0005 quyết định 3: hai không gian tên khác nhau ở DTO đầu ra, không khác ở
 * SQL. Hai truy vấn song song cho cùng một câu hỏi sớm muộn cũng lệch, và lệch theo hướng nới lỏng
 * thì không ai thấy.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceDetailService {

    /** Ba nhóm đầu vét hết bốn quan hệ; phần còn lại rơi vào {@code other} chứ không biến mất. */
    private static final Set<EvidenceLinkRelation> TEST_RELATIONS =
            Set.of(EvidenceLinkRelation.SUPPORTS, EvidenceLinkRelation.CONTRADICTS);

    private static final Set<EvidenceType> TEST_TYPES =
            Set.of(EvidenceType.TEST_RESULT, EvidenceType.FAILED_TEST, EvidenceType.STACK_TRACE);

    private final EvidenceQueryService evidenceQueryService;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final WarningRepository warningRepository;
    private final WarningLocationRepository warningLocationRepository;
    private final TestCaseRepository testCaseRepository;

    public EvidenceDetailService(
            EvidenceQueryService evidenceQueryService,
            EvidenceRecordRepository evidenceRecordRepository,
            WarningRepository warningRepository,
            WarningLocationRepository warningLocationRepository,
            TestCaseRepository testCaseRepository) {
        this.evidenceQueryService = evidenceQueryService;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.warningRepository = warningRepository;
        this.warningLocationRepository = warningLocationRepository;
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * @param ownerId phân quyền xảy ra Ở ĐÂY, không ở {@code EvidenceQueryService}: đường tool dùng
     *     service token và {@code experiments/runner.py} chạy batch không có phiên người dùng nào
     *     (ADR 0005 quyết định 2). Không thuộc về bạn ⇒ 404, không phải 403 — không rò rỉ sự tồn tại
     */
    public EvidenceDetailResponse detail(UUID runId, String shortId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        AnalysisRun run = evidenceQueryService.ensureRunQueryable(runId);
        EvidenceRecord anchor = evidenceQueryService.getEvidence(runId, shortId);

        List<LinkedEvidence> codeContext =
                evidenceQueryService.relatedPairs(anchor, Set.of(EvidenceLinkRelation.LOCATED_IN), null);
        List<LinkedEvidence> tests = evidenceQueryService.relatedPairs(anchor, TEST_RELATIONS, TEST_TYPES);
        List<LinkedEvidence> coverage =
                evidenceQueryService.relatedPairs(anchor, Set.of(EvidenceLinkRelation.COVERS), null);

        // Mọi liên kết KHÔNG rơi vào ba nhóm trên vẫn phải hiện ra. Nhóm 'other' tồn tại để một
        // quan hệ mới (vd CROSS_TOOL_AGREEMENT) không biến mất khỏi giao diện chỉ vì chưa ai nghĩ
        // tới nó — im lặng bỏ dữ liệu là đúng thứ dự án này chống.
        Set<UUID> daPhanNhom = new java.util.HashSet<>();
        codeContext.forEach(pair -> daPhanNhom.add(pair.link().getId()));
        tests.forEach(pair -> daPhanNhom.add(pair.link().getId()));
        coverage.forEach(pair -> daPhanNhom.add(pair.link().getId()));
        List<LinkedEvidence> other =
                evidenceQueryService.relatedPairs(anchor, Set.of(EvidenceLinkRelation.values()), null).stream()
                        .filter(pair -> !daPhanNhom.contains(pair.link().getId()))
                        .toList();

        List<LinkedEvidence> tatCa = new ArrayList<>(codeContext);
        tatCa.addAll(tests);
        tatCa.addAll(coverage);
        tatCa.addAll(other);
        Map<UUID, TestCaseSummaryView> testCaseById = loadTestCases(tatCa);

        return new EvidenceDetailResponse(
                anchor.getId(),
                anchor.getShortId(),
                runId,
                run.getSnapshotId(),
                anchor.getType(),
                anchor.getSource(),
                anchor.getSummary(),
                anchor.getFilePath(),
                anchor.getStartLine(),
                anchor.getEndLine(),
                anchor.getSymbol(),
                anchor.getType() == EvidenceType.STATIC_WARNING ? warningDetail(anchor.getId()) : null,
                toViews(codeContext, testCaseById),
                toViews(tests, testCaseById),
                toViews(coverage, testCaseById),
                toViews(other, testCaseById),
                tatCa.size());
    }

    private WarningDetailView warningDetail(UUID evidenceId) {
        Warning warning = warningRepository.findById(evidenceId).orElse(null);
        if (warning == null) {
            return null;
        }
        List<WarningLocationView> locations = warningLocationRepository
                .findByEvidenceRecordIdOrderByOrdinalAsc(evidenceId)
                .stream()
                .map(EvidenceDetailService::toLocationView)
                .toList();
        return new WarningDetailView(
                warning.getTool(),
                warning.getRuleId(),
                warning.getRuleIdNormalized(),
                warning.getSeverityRaw(),
                warning.getSeverityNormalized(),
                warning.isHasPrimaryLocation(),
                warning.getCweIds() == null ? null : Arrays.asList(warning.getCweIds()),
                locations);
    }

    private static WarningLocationView toLocationView(WarningLocation location) {
        return new WarningLocationView(
                location.getOrdinal(),
                location.isPrimary(),
                location.getRoleRaw(),
                location.getPathRaw(),
                location.getFilePath(),
                location.getStartLine(),
                location.getEndLine(),
                location.getClassName(),
                location.getMethodName());
    }

    /**
     * Nạp chi tiết test theo LÔ, không phải theo từng thẻ — cùng lý do {@code WarningView} dùng
     * constructor projection: một màn hình có thể có hàng chục liên kết test.
     */
    private Map<UUID, TestCaseSummaryView> loadTestCases(List<LinkedEvidence> pairs) {
        Set<UUID> testIds = pairs.stream()
                .filter(pair -> pair.record().getType() == EvidenceType.TEST_RESULT
                        || pair.record().getType() == EvidenceType.FAILED_TEST)
                .map(pair -> pair.record().getId())
                .collect(Collectors.toSet());
        if (testIds.isEmpty()) {
            return Map.of();
        }
        // short_id của evidence STACK_TRACE đi kèm: giao diện cần nó để dựng link "xem stack trace
        // đầy đủ" mà không phải đoán.
        Map<UUID, String> shortIdById = evidenceRecordRepository
                .findAllById(testCaseRepository.findAllById(testIds).stream()
                        .map(TestCase::getStackTraceEvidenceId)
                        .filter(java.util.Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(EvidenceRecord::getId, EvidenceRecord::getShortId));

        return testCaseRepository.findAllById(testIds).stream()
                .collect(Collectors.toMap(
                        TestCase::getEvidenceRecordId,
                        testCase -> new TestCaseSummaryView(
                                testCase.getClassName(),
                                testCase.getMethodName(),
                                testCase.getStatus(),
                                testCase.getFailureKind(),
                                testCase.getFailureType(),
                                testCase.getFailureMessage(),
                                testCase.getDurationMs(),
                                testCase.getStackTraceEvidenceId() == null
                                        ? null
                                        : shortIdById.get(testCase.getStackTraceEvidenceId()))));
    }

    private static List<RelatedEvidenceView> toViews(
            List<LinkedEvidence> pairs, Map<UUID, TestCaseSummaryView> testCaseById) {
        return pairs.stream()
                .map(pair -> new RelatedEvidenceView(
                        pair.record().getId(),
                        pair.record().getShortId(),
                        pair.record().getType(),
                        pair.record().getSource(),
                        pair.record().getSummary(),
                        pair.record().getFilePath(),
                        pair.record().getStartLine(),
                        pair.record().getEndLine(),
                        pair.record().getSymbol(),
                        pair.link().getRelation(),
                        pair.link().getMethod(),
                        pair.link().getWeight().doubleValue(),
                        pair.link().getRationale(),
                        pair.outgoing(),
                        testCaseById.get(pair.record().getId())))
                .toList();
    }
}
