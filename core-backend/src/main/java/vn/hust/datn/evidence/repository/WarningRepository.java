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
     *
     * <p><b>{@code filePath} khớp theo MỘT PHẦN đường dẫn, không phân biệt hoa thường.</b> Bản đầu
     * dùng {@code e.filePath = :filePath} và biến ô lọc thành thứ vô dụng: đường dẫn Java thật dài
     * tới 60+ ký tự nên không ai gõ đủ, và gõ thiếu một ký tự thì bảng rỗng — trông y hệt "không có
     * cảnh báo nào ở file này". Không có lỗi nào để lần theo, chỉ có một ô tìm kiếm không bao giờ
     * ra kết quả. Cùng khuôn với {@code EvidenceRecordRepository.searchInRun}, vốn đã dùng
     * {@code like} từ đầu.
     *
     * <p><b>{@code cast(:filePath as string)} là BẮT BUỘC, không phải cho đẹp.</b> Khi tham số bằng
     * {@code null}, Hibernate không suy được kiểu nên bind nó dạng {@code bytea}, và Postgres đổ
     * {@code ERROR: function lower(bytea) does not exist} — tức là **không lọc gì cả cũng ra HTTP
     * 500**. Bẫy phụ: lỗi này phụ thuộc THỨ TỰ CHẠY. Nếu một lời gọi có {@code filePath} thật đi
     * trước, prepared statement đã được tạo với tham số kiểu text và các lời gọi {@code null} sau
     * đó ăn theo — nên bộ test có thể xanh hay đỏ tuỳ thứ tự method. Ép kiểu tường minh thì không
     * còn phụ thuộc vào may rủi đó.
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
                      and (:filePath is null
                           or lower(e.filePath) like lower(concat('%', cast(:filePath as string), '%')))
                    order by w.severityRank asc, e.seqInRun asc
                    """,
            countQuery =
                    """
                    select count(w) from Warning w join w.evidenceRecord e
                    where e.analysisRunId = :runId
                      and (:tool is null or w.tool = :tool)
                      and (:severity is null or w.severityNormalized = :severity)
                      and (:filePath is null
                           or lower(e.filePath) like lower(concat('%', cast(:filePath as string), '%')))
                    """)
    Page<WarningView> findForUi(
            @Param("runId") UUID runId,
            @Param("tool") WarningTool tool,
            @Param("severity") WarningSeverity severity,
            @Param("filePath") String filePath,
            Pageable pageable);
}
