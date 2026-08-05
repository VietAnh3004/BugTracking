package vn.hust.datn.evidence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.WarningView;

public interface WarningRepository extends JpaRepository<Warning, UUID> {

    // Constructor projection, KHÔNG map từ entity: warning.evidenceRecord là @OneToOne LAZY một
    // chiều, map từ entity gây LazyInitializationException ngoài transaction hoặc N+1 trong list.
    // Đây cũng chính là hình dạng SQL mà tuần 15 dùng để tính chỉ số thực nghiệm.
    @Query("""
            select new vn.hust.datn.evidence.dto.WarningView(
                e.id, e.shortId, e.analysisRunId, e.filePath, e.startLine, e.endLine,
                e.symbol, e.summary, w.tool, w.ruleId, w.ruleIdNormalized,
                w.severityRaw, w.severityNormalized, w.hasPrimaryLocation)
            from Warning w join w.evidenceRecord e
            where e.analysisRunId = :runId and e.shortId = :shortId
            """)
    Optional<WarningView> findFlatByRunAndShortId(@Param("runId") UUID runId, @Param("shortId") String shortId);

    @Query("""
            select new vn.hust.datn.evidence.dto.WarningView(
                e.id, e.shortId, e.analysisRunId, e.filePath, e.startLine, e.endLine,
                e.symbol, e.summary, w.tool, w.ruleId, w.ruleIdNormalized,
                w.severityRaw, w.severityNormalized, w.hasPrimaryLocation)
            from Warning w join w.evidenceRecord e
            where e.analysisRunId = :runId
            """)
    Page<WarningView> findFlatByRun(@Param("runId") UUID runId, Pageable pageable);

    /**
     * Danh sách cảnh báo cho màn hình UI: lọc tuỳ chọn theo tool/mức nghiêm trọng/file.
     *
     * <p>{@code order by w.severityRank} chứ TUYỆT ĐỐI KHÔNG phải {@code severityNormalized}:
     * cột sau là TEXT nên sắp theo chữ cái ra HIGH, INFO, LOW, MEDIUM — sai mà rất khó thấy vì
     * HIGH vẫn đứng đầu. {@code seqInRun} làm khoá phụ để thứ tự ổn định giữa hai lần gọi.
     */
    @Query(
            value =
                    """
                    select new vn.hust.datn.evidence.dto.WarningView(
                        e.id, e.shortId, e.analysisRunId, e.filePath, e.startLine, e.endLine,
                        e.symbol, e.summary, w.tool, w.ruleId, w.ruleIdNormalized,
                        w.severityRaw, w.severityNormalized, w.hasPrimaryLocation)
                    from Warning w join w.evidenceRecord e
                    where e.analysisRunId = :runId
                      and (:tool is null or w.tool = :tool)
                      and (:severity is null or w.severityNormalized = :severity)
                      and (:filePath is null or e.filePath = :filePath)
                    order by w.severityRank asc, e.seqInRun asc
                    """,
            countQuery =
                    """
                    select count(w) from Warning w join w.evidenceRecord e
                    where e.analysisRunId = :runId
                      and (:tool is null or w.tool = :tool)
                      and (:severity is null or w.severityNormalized = :severity)
                      and (:filePath is null or e.filePath = :filePath)
                    """)
    Page<WarningView> findForUi(
            @Param("runId") UUID runId,
            @Param("tool") WarningTool tool,
            @Param("severity") WarningSeverity severity,
            @Param("filePath") String filePath,
            Pageable pageable);
}
