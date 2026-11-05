package vn.hust.datn.evidence.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;

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

    /**
     * Hạ mọi run còn {@code RUNNING} bắt đầu TRƯỚC {@code cutoff} xuống {@code FAILED} — xem
     * {@code OrphanedRunRecovery}. Điều kiện {@code status} nằm ngay trong WHERE nên một run vừa
     * kịp kết thúc thật không bị ghi đè.
     *
     * <p>{@code @Transactional} ngay tại đây vì người gọi duy nhất gọi từ một event listener,
     * không có transaction bao ngoài.
     */
    @Transactional
    @Modifying
    @Query("UPDATE AnalysisRun r SET r.status = :failed, r.errorMessage = :reason, r.finishedAt = :finishedAt "
            + "WHERE r.status = :running AND r.startedAt < :cutoff")
    int failRunningStartedBefore(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("reason") String reason,
            @Param("finishedAt") OffsetDateTime finishedAt,
            @Param("running") AnalysisRunStatus running,
            @Param("failed") AnalysisRunStatus failed);
}
