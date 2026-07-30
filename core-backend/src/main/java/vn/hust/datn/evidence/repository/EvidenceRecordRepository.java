package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceType;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, UUID> {

    // short_id chỉ unique TRONG PHẠM VI một run (UNIQUE(analysis_run_id, short_id)) — không có
    // run id thì lookup vô nghĩa (ADR 0004 §4, kế hoạch tuần 4 quy tắc #1 cho 5 tool).
    Optional<EvidenceRecord> findByAnalysisRunIdAndShortId(UUID analysisRunId, String shortId);

    // Dùng để phân biệt UNKNOWN_ID (short_id không tồn tại ở đâu cả) với WRONG_RUN (tồn tại
    // nhưng ở run khác) — không có index riêng trên short_id (unique index là composite,
    // không phục vụ lookup thiếu cột đầu). Dữ liệu tuần 4 còn nhỏ nên seq scan chấp nhận được;
    // TODO(V3): thêm index riêng nếu volume tăng.
    boolean existsByShortId(String shortId);

    // Khớp idx_evidence_record_run_type (analysis_run_id, type).
    Page<EvidenceRecord> findByAnalysisRunIdAndType(UUID analysisRunId, EvidenceType type, Pageable pageable);

    // Fallback định vị CODE_CONTEXT theo (run, file, khoảng dòng) khi chưa có evidence_link
    // LOCATED_IN thật (EvidenceLinker là tuần 10-11) — khớp idx_evidence_record_location.
    @Query("""
            select e from EvidenceRecord e
            where e.analysisRunId = :runId
              and e.filePath = :filePath
              and e.startLine between :fromLine and :toLine
            """)
    List<EvidenceRecord> findNearLocation(
            @Param("runId") UUID runId,
            @Param("filePath") String filePath,
            @Param("fromLine") int fromLine,
            @Param("toLine") int toLine);

    // search_evidence tuần 4: ILIKE thuần, không BM25/vector (ADR 0004 §7 cố ý chưa chốt số chiều
    // embedding).
    @Query("""
            select e from EvidenceRecord e
            where e.analysisRunId = :runId
              and (:filePath is null or e.filePath = :filePath)
              and (lower(e.summary) like lower(concat('%', :query, '%'))
                   or lower(e.filePath) like lower(concat('%', :query, '%'))
                   or lower(e.symbol) like lower(concat('%', :query, '%')))
            """)
    List<EvidenceRecord> search(@Param("runId") UUID runId, @Param("query") String query, @Param("filePath") String filePath, Pageable pageable);
}
