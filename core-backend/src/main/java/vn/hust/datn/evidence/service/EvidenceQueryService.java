package vn.hust.datn.evidence.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.EvidenceLink;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.dto.WarningView;
import vn.hust.datn.evidence.dto.tool.ToolEvidenceItem;
import vn.hust.datn.evidence.exception.EvidenceNotAWarningException;
import vn.hust.datn.evidence.exception.EvidenceNotFoundException;
import vn.hust.datn.evidence.exception.EvidenceWrongRunException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.exception.RunNotQueryableException;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;
import vn.hust.datn.evidence.repository.EvidenceLinkRepository;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;
import vn.hust.datn.evidence.repository.WarningRepository;

/**
 * Service trung tâm cho cả 5 evidence tool VÀ (sau này) endpoint UI — chúng khác nhau ở DTO đầu
 * ra, không khác ở SQL (xem ADR 0005). Tách controller tool/UI mà viết lại query ở đây hai lần
 * mới là over-engineering thật.
 *
 * <p>Mọi method public đều gọi {@link #ensureRunQueryable(UUID)} trước — ngăn "rỗng vì chưa
 * build" (run RUNNING/FAILED) lẫn với "rỗng vì đúng là rỗng" (run SUCCEEDED nhưng không có liên
 * kết nào). Không có bước này, tool trả {@code []} cho cả hai tình huống và không ai phân biệt
 * được tới tận tuần 12-13 khi chạy agent loop thật.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceQueryService {

    /** Bằng chứng sinh từ một lần chạy test — xem Javadoc {@link #getRelatedTests}. */
    private static final Set<EvidenceType> TEST_EVIDENCE_TYPES =
            Set.of(EvidenceType.TEST_RESULT, EvidenceType.FAILED_TEST, EvidenceType.STACK_TRACE);

    /**
     * Thứ tự tất định cho mọi danh sách item: relation → weight GIẢM DẦN → short_id.
     *
     * <p>{@code short_id} là bộ phá hoà cuối cùng và nó luôn duy nhất trong một run, nên hai lần
     * chạy trên cùng dữ liệu không bao giờ cho hai thứ tự khác nhau.
     */
    private static final Comparator<Map.Entry<EvidenceLink, EvidenceRecord>> ITEM_ORDER = Comparator.comparing(
                    (Map.Entry<EvidenceLink, EvidenceRecord> entry) -> entry.getKey().getRelation())
            .thenComparing(entry -> entry.getKey().getWeight(), Comparator.reverseOrder())
            .thenComparing(entry -> entry.getValue().getShortId());

    private final AnalysisRunRepository analysisRunRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final WarningRepository warningRepository;
    private final EvidenceLinkRepository evidenceLinkRepository;

    public EvidenceQueryService(
            AnalysisRunRepository analysisRunRepository,
            EvidenceRecordRepository evidenceRecordRepository,
            WarningRepository warningRepository,
            EvidenceLinkRepository evidenceLinkRepository) {
        this.analysisRunRepository = analysisRunRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.warningRepository = warningRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
    }

    public WarningView getWarning(UUID runId, String shortId) {
        ensureRunQueryable(runId);
        requireEvidenceInRun(runId, shortId);
        return warningRepository
                .findFlatByRunAndShortId(runId, shortId)
                .orElseThrow(() -> new EvidenceNotAWarningException(shortId));
    }

    /**
     * Tuần 4: query evidence_link LOCATED_IN trước, fallback theo (run, file, khoảng dòng) nếu
     * chưa có link. Cả hai nguồn đều rỗng cho tới khi EvidenceLinker + JavaParser ra đời (tuần
     * 10-11) — đúng như thiết kế, không phải lỗi.
     */
    public List<ToolEvidenceItem> getCodeContext(UUID runId, String shortId, int contextLines) {
        ensureRunQueryable(runId);
        EvidenceRecord anchor = requireEvidenceInRun(runId, shortId);

        List<ToolEvidenceItem> viaLink = relatedEvidence(anchor, Set.of(EvidenceLinkRelation.LOCATED_IN), null);
        if (!viaLink.isEmpty()) {
            return viaLink;
        }

        if (anchor.getFilePath() == null || anchor.getStartLine() == null) {
            return List.of();
        }
        int from = Math.max(1, anchor.getStartLine() - contextLines);
        int to = (anchor.getEndLine() != null ? anchor.getEndLine() : anchor.getStartLine()) + contextLines;
        return evidenceRecordRepository.findNearLocation(runId, anchor.getFilePath(), from, to).stream()
                .filter(e -> e.getType() == EvidenceType.CODE_CONTEXT && !e.getId().equals(anchor.getId()))
                .map(e -> toItem(e, null, null))
                .toList();
    }

    /**
     * "Test liên quan" phải là bằng chứng VỀ TEST, không phải bất kỳ evidence nào tình cờ mang
     * quan hệ SUPPORTS/CONTRADICTS. Không lọc type thì một bằng chứng độ phủ mang SUPPORTS
     * (EvidenceLinker tầng 2 sinh ra thật, method {@code METHOD_AGGREGATE}) cũng lọt vào đây, và
     * LLM đọc xong sẽ nói "có test ủng hộ" trong khi không có test nào.
     *
     * <p>{@code STACK_TRACE} nằm trong bộ lọc dù link tầng 3 trỏ về evidence {@code FAILED_TEST}:
     * nó vẫn là bằng chứng sinh từ một lần chạy test, và nếu về sau có tầng nào liên kết thẳng
     * stack trace thì loại bằng chứng THUYẾT PHỤC NHẤT không bị lặng lẽ rơi khỏi danh sách.
     */
    public List<ToolEvidenceItem> getRelatedTests(UUID runId, String shortId, Set<EvidenceLinkRelation> relations) {
        ensureRunQueryable(runId);
        EvidenceRecord anchor = requireEvidenceInRun(runId, shortId);
        Set<EvidenceLinkRelation> effective =
                (relations == null || relations.isEmpty()) ? Set.of(EvidenceLinkRelation.SUPPORTS, EvidenceLinkRelation.CONTRADICTS) : relations;
        return relatedEvidence(anchor, effective, TEST_EVIDENCE_TYPES);
    }

    public List<ToolEvidenceItem> getCoverage(UUID runId, String shortId) {
        ensureRunQueryable(runId);
        EvidenceRecord anchor = requireEvidenceInRun(runId, shortId);
        return relatedEvidence(anchor, Set.of(EvidenceLinkRelation.COVERS), Set.of(EvidenceType.COVERAGE_INFORMATION));
    }

    public List<EvidenceRecord> searchEvidence(UUID runId, String query, Set<EvidenceType> types, String filePath, int limit) {
        ensureRunQueryable(runId);
        List<EvidenceRecord> results = evidenceRecordRepository.search(runId, query, filePath, PageRequest.of(0, limit));
        if (types == null || types.isEmpty()) {
            return results;
        }
        return results.stream().filter(e -> types.contains(e.getType())).toList();
    }

    /**
     * Đơn vị trả về là MỘT LINK, không phải một evidence.
     *
     * <p>Bản trước gom vào {@code Map<UUID, EvidenceLink>} khoá theo evidence phía đối diện. Vì
     * {@code UNIQUE (from_evidence_id, to_evidence_id, relation)} cho phép CÙNG một cặp mang nhiều
     * quan hệ, {@code put} sau đè {@code put} trước và một quan hệ biến mất IM LẶNG. Tình huống
     * này có thật ngay khi EvidenceLinker chạy: một test vừa phủ dòng cảnh báo (CONTRADICTS) vừa
     * có stack frame trỏ đúng dòng đó (SUPPORTS).
     *
     * <p>Thứ tự TẤT ĐỊNH: {@code relation}, rồi {@code weight} giảm dần (bằng chứng mạnh lên
     * trước), rồi {@code short_id} để phá hoà hoàn toàn. Đây là dữ liệu đi thẳng vào prompt LLM —
     * thứ tự đổi giữa hai lần chạy thì thí nghiệm tái lập tuần 15-16 mất căn cứ, mà lỗi đó không
     * có triệu chứng nào.
     */
    private List<ToolEvidenceItem> relatedEvidence(
            EvidenceRecord anchor, Set<EvidenceLinkRelation> relations, Set<EvidenceType> typeFilter) {
        return relatedPairs(anchor, relations, typeFilter).stream()
                .map(pair -> toItem(pair.record(), pair.link().getRelation(), pair.link()))
                .toList();
    }

    /**
     * Truy vấn dùng chung cho cả đường tool lẫn đường UI — xem {@link LinkedEvidence}.
     *
     * <p>Public để {@code EvidenceDetailService} gọi được mà không phải viết lại truy vấn thứ hai
     * (ADR 0005 quyết định 3). Người gọi tự lo phần map sang DTO của mình.
     */
    public List<LinkedEvidence> relatedPairs(
            EvidenceRecord anchor, Set<EvidenceLinkRelation> relations, Set<EvidenceType> typeFilter) {
        List<EvidenceLink> links = evidenceLinkRepository
                .findByFromEvidenceIdOrToEvidenceId(anchor.getId(), anchor.getId())
                .stream()
                .filter(l -> relations.contains(l.getRelation()))
                .toList();
        if (links.isEmpty()) {
            return List.of();
        }

        // EvidenceLink lưu UUID trần, không @ManyToOne -> không join JPQL được. Hai bước: gom id
        // phía đối diện, rồi findAllById (xem EvidenceLinkRepository).
        Set<UUID> oppositeIds = links.stream().map(l -> oppositeIdOf(l, anchor.getId())).collect(Collectors.toSet());
        Map<UUID, EvidenceRecord> recordById = evidenceRecordRepository.findAllById(oppositeIds).stream()
                .collect(Collectors.toMap(EvidenceRecord::getId, Function.identity()));

        return links.stream()
                .map(link -> Map.entry(link, recordById.get(oppositeIdOf(link, anchor.getId()))))
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> typeFilter == null || typeFilter.contains(entry.getValue().getType()))
                .sorted(ITEM_ORDER)
                .map(entry -> new LinkedEvidence(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getKey().getFromEvidenceId().equals(anchor.getId())))
                .toList();
    }

    /** Neo của một màn hình chi tiết. Public vì đường UI cần chính phép phân loại lỗi này. */
    public EvidenceRecord getEvidence(UUID runId, String shortId) {
        ensureRunQueryable(runId);
        return requireEvidenceInRun(runId, shortId);
    }

    private static UUID oppositeIdOf(EvidenceLink link, UUID anchorId) {
        return link.getFromEvidenceId().equals(anchorId) ? link.getToEvidenceId() : link.getFromEvidenceId();
    }

    private ToolEvidenceItem toItem(EvidenceRecord record, EvidenceLinkRelation relation, EvidenceLink link) {
        return new ToolEvidenceItem(
                record.getShortId(),
                record.getType(),
                record.getSummary(),
                record.getFilePath(),
                record.getStartLine(),
                record.getSymbol(),
                relation,
                link == null ? null : link.getMethod(),
                link == null ? 0.0 : link.getWeight().doubleValue());
    }

    /**
     * Phân biệt UNKNOWN_ID (short_id không tồn tại ở đâu cả) với WRONG_RUN (tồn tại nhưng ở run
     * khác) — hai tình huống tương ứng {@code ClaimCitationResolution} đã có, CitationValidator
     * tuần 12-13 dùng lại nguyên xi.
     */
    private EvidenceRecord requireEvidenceInRun(UUID runId, String shortId) {
        return evidenceRecordRepository
                .findByAnalysisRunIdAndShortId(runId, shortId)
                .orElseThrow(() -> evidenceRecordRepository.existsByShortId(shortId)
                        ? new EvidenceWrongRunException(shortId)
                        : new EvidenceNotFoundException(shortId));
    }

    /**
     * Điểm cắm authorization mà ADR 0005 nói tới — CHƯA CÓ caller nào ở tuần 5-7 (REST resource UI
     * cho evidence hoãn tới tuần 10-11), viết sẵn để khoá tên chỗ cắm trước khi có nhu cầu thật.
     *
     * <p>KHÔNG đụng vào {@link #ensureRunQueryable(UUID)}: đường tool ({@code /api/v1/tools/**})
     * dùng service token, phân quyền đã xảy ra ở entry point theo ADR 0005, và
     * {@code experiments/runner.py} (thực nghiệm cốt lõi của đồ án) chạy batch không có phiên
     * người dùng — đổi hành vi của {@code ensureRunQueryable} sẽ phá cả hai.
     */
    public void requireRunOwnedBy(UUID runId, UUID ownerId) {
        UUID actualOwnerId = analysisRunRepository
                .findOwnerIdByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("analysis_run", runId));
        if (!actualOwnerId.equals(ownerId)) {
            throw new ResourceNotFoundException("analysis_run", runId);
        }
    }

    /**
     * Public để đường UI ({@code AnalysisRunQueryService}) dùng chung ĐÚNG một định nghĩa "run đã
     * truy vấn được" với đường tool. Hai định nghĩa song song sớm muộn cũng lệch, và lệch theo
     * hướng nới lỏng thì không ai thấy.
     */
    public AnalysisRun ensureRunQueryable(UUID runId) {
        AnalysisRun run =
                analysisRunRepository.findById(runId).orElseThrow(() -> new ResourceNotFoundException("analysis_run", runId));
        if (run.getStatus() != AnalysisRunStatus.SUCCEEDED && run.getStatus() != AnalysisRunStatus.SUCCEEDED_CLEAN) {
            throw new RunNotQueryableException(runId, run.getStatus());
        }
        return run;
    }
}
