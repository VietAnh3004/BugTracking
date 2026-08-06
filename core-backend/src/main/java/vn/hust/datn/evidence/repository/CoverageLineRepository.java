package vn.hust.datn.evidence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.CoverageLine;
import vn.hust.datn.evidence.domain.CoverageLineId;
import vn.hust.datn.evidence.service.linker.CoverageLineRow;

public interface CoverageLineRepository extends JpaRepository<CoverageLine, CoverageLineId> {

    /**
     * Độ phủ CHỈ của những file có cảnh báo, không phải cả run.
     *
     * <p>Đây là điểm chống phình quan trọng nhất của EvidenceLinker: {@code coverage_line} là bảng
     * lớn nhanh nhất (một dòng cho MỖI dòng mã thực thi được — vulnshop đã 174, một repo thật dễ
     * lên hàng trăm nghìn). Cảnh báo thì luôn chỉ nằm ở một nhúm file. Nạp cả run vào bộ nhớ để
     * rồi lọc là tự tạo giới hạn quy mô mà không đổi lấy gì.
     *
     * <p>Người gọi phải chia {@code filePaths} thành khối ≤1000: Postgres chặn ở 65535 bind
     * parameter, và vượt ngưỡng đó thì lỗi chỉ lộ ra trên repo lớn — tức là ở đúng chỗ khó chẩn
     * đoán nhất.
     */
    @Query(
            """
            select new vn.hust.datn.evidence.service.linker.CoverageLineRow(
                e.id, e.filePath, cl.lineNumber, cl.mi, cl.ci, cl.mb, cl.cb)
            from CoverageLine cl, EvidenceRecord e
            where cl.evidenceRecordId = e.id
              and e.analysisRunId = :runId
              and e.filePath in :filePaths
            order by e.filePath, cl.lineNumber
            """)
    List<CoverageLineRow> findByRunAndFilePaths(
            @Param("runId") UUID runId, @Param("filePaths") Collection<String> filePaths);
}
