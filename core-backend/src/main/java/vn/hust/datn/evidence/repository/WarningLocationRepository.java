package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.WarningLocation;
import vn.hust.datn.evidence.domain.WarningLocationId;
import vn.hust.datn.evidence.service.linker.WarningLocationRow;

public interface WarningLocationRepository extends JpaRepository<WarningLocation, WarningLocationId> {

    /**
     * Mọi vị trí cảnh báo ĐỐI CHIẾU ĐƯỢC của một run, dạng phẳng.
     *
     * <p>Constructor projection chứ không trả entity: {@code WarningLocation} không có association
     * sang {@code EvidenceRecord} (nó khoá bằng UUID trần), nên trả entity thì phải {@code findAllById}
     * lượt hai chỉ để lấy {@code short_id}. Cùng khuôn {@code WarningRepository.findFlatByRun}.
     *
     * <p>Join theta vì không có association — Hibernate dịch thành inner join bình thường.
     *
     * <p>EvidenceLinker duyệt TỪNG VỊ TRÍ chứ không chỉ vị trí chính của {@code evidence_record}:
     * 3/28 cảnh báo thật của vulnshop có nhiều vị trí, một cái trỏ tới 3 file khác nhau. Đi theo
     * cột {@code evidence_record.start_line} là lặng lẽ bỏ 2/3 bằng chứng.
     */
    @Query(
            """
            select new vn.hust.datn.evidence.service.linker.WarningLocationRow(
                e.id, e.shortId, wl.ordinal, wl.filePath, wl.startLine, wl.endLine, wl.className, wl.methodName)
            from WarningLocation wl, EvidenceRecord e
            where wl.evidenceRecordId = e.id
              and e.analysisRunId = :runId
              and wl.filePath is not null
              and wl.startLine is not null
            order by e.seqInRun, wl.ordinal
            """)
    List<WarningLocationRow> findResolvedRowsByRun(@Param("runId") UUID runId);

    /**
     * Mọi vị trí của MỘT cảnh báo, kể cả những vị trí KHÔNG đối chiếu được với snapshot.
     *
     * <p>Khác {@link #findResolvedRowsByRun}: ở đây giữ cả hàng có {@code file_path} NULL, vì màn
     * hình chi tiết phải nói được "analyzer khai đường dẫn này nhưng không tìm thấy trong mã nguồn
     * đã tải lên" — im lặng bỏ vị trí đi là giấu đúng thứ người dùng cần biết để chẩn đoán.
     */
    List<WarningLocation> findByEvidenceRecordIdOrderByOrdinalAsc(UUID evidenceRecordId);
}
