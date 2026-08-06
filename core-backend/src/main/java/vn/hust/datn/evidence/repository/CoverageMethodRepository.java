package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.CoverageMethod;
import vn.hust.datn.evidence.service.linker.CoverageMethodRow;

public interface CoverageMethodRepository extends JpaRepository<CoverageMethod, UUID> {

    /**
     * Tổng hợp độ phủ mức method của cả run — nhỏ hơn {@code coverage_line} nhiều bậc (74 hàng
     * trên vulnshop so với 174), nên nạp trọn là hợp lý.
     *
     * <p>Khớp {@code idx_coverage_method_symbol (class_name, method_name)}.
     */
    @Query(
            """
            select new vn.hust.datn.evidence.service.linker.CoverageMethodRow(
                cm.evidenceRecordId, cm.className, cm.methodName, cm.coveredInstructions)
            from CoverageMethod cm, EvidenceRecord e
            where cm.evidenceRecordId = e.id
              and e.analysisRunId = :runId
            order by cm.className, cm.methodName
            """)
    List<CoverageMethodRow> findRowsByRun(@Param("runId") UUID runId);
}
