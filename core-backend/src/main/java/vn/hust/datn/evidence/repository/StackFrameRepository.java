package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.StackFrame;
import vn.hust.datn.evidence.domain.StackFrameId;
import vn.hust.datn.evidence.service.linker.StackFrameRow;

public interface StackFrameRepository extends JpaRepository<StackFrame, StackFrameId> {

    /**
     * Frame trỏ về MÃ PRODUCTION và đối chiếu được với snapshot — đúng tập mà EvidenceLinker tầng
     * 3 cần.
     *
     * <p>Khớp {@code idx_stack_frame_production (file_path, line_number) WHERE
     * frame_kind = 'PRODUCTION' AND file_path IS NOT NULL}, index đã dựng sẵn ở V5 kèm comment
     * "truy vấn chủ đạo của EvidenceLinker tuần 10-11".
     *
     * <p>Trên vulnshop chỉ 7/44 frame lọt qua bộ lọc này — và đó chính là lý do tầng 3 KHÔNG đủ
     * một mình: 3/5 test hỏng không có frame production nào (docs/evidence-model.md phát hiện 2).
     */
    @Query(
            """
            select new vn.hust.datn.evidence.service.linker.StackFrameRow(
                sf.evidenceRecordId, sf.ordinal, sf.filePath, sf.lineNumber, sf.rawLine)
            from StackFrame sf, EvidenceRecord e
            where sf.evidenceRecordId = e.id
              and e.analysisRunId = :runId
              and sf.frameKind = vn.hust.datn.evidence.domain.StackFrameKind.PRODUCTION
              and sf.filePath is not null
              and sf.lineNumber is not null
            order by sf.evidenceRecordId, sf.ordinal
            """)
    List<StackFrameRow> findProductionFramesByRun(@Param("runId") UUID runId);
}
