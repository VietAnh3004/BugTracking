package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.AnalysisRun;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

    // Khớp index idx_analysis_run_snapshot (snapshot_id, started_at DESC) trong V2__evidence_model.sql.
    List<AnalysisRun> findBySnapshotIdOrderByStartedAtDesc(java.util.UUID snapshotId);
}
