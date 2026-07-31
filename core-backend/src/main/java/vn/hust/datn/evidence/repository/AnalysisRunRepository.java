package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.AnalysisRun;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

    // Khớp index idx_analysis_run_snapshot (snapshot_id, started_at DESC) trong V2__evidence_model.sql.
    List<AnalysisRun> findBySnapshotIdOrderByStartedAtDesc(java.util.UUID snapshotId);

    // Ba entity không có quan hệ JPA (analysis_run/repository_snapshot/project đều dùng UUID
    // trần, xem Javadoc AnalysisRun) - "theta join" tường minh qua WHERE thay vì navigation
    // property. Đây là điểm cắm authorization mà ADR 0005 nói tới cho /api/v1/tools/**, chưa có
    // caller nào ở tuần 5-7 (REST resource UI cho evidence hoãn tới tuần 10-11) nhưng viết sẵn
    // để khoá tên chỗ cắm - không ai đi đường khác khi cần.
    @Query(
            "SELECT p.ownerId FROM AnalysisRun r, RepositorySnapshot s, Project p "
                    + "WHERE r.id = :runId AND r.snapshotId = s.id AND s.projectId = p.id")
    Optional<UUID> findOwnerIdByRunId(@Param("runId") UUID runId);
}
