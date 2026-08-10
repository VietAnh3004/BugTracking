package vn.hust.datn.evidence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.GroundTruthLabel;
import vn.hust.datn.evidence.domain.GroundTruthLabelId;
import vn.hust.datn.evidence.domain.GroundTruthSource;

public interface GroundTruthLabelRepository extends JpaRepository<GroundTruthLabel, GroundTruthLabelId> {

    /**
     * Nhãn chuẩn của mọi cảnh báo trong một run.
     *
     * <p>Lọc theo {@code source} ngay trong truy vấn: trộn nhãn của người gán chính với nhãn của
     * người gán mù thứ hai sẽ làm hỏng cả hai chỉ số — accuracy tính hai lần trên cùng một cảnh
     * báo, và kappa mất mất một trong hai vế để so.
     */
    @Query(
            """
            select g from GroundTruthLabel g
            where g.source = :source
              and g.evidenceRecordId in (
                  select e.id from EvidenceRecord e where e.analysisRunId = :runId
              )
            """)
    List<GroundTruthLabel> findByRunAndSource(
            @Param("runId") java.util.UUID runId, @Param("source") GroundTruthSource source);
}
