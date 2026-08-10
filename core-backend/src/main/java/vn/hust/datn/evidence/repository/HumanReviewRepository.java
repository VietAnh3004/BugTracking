package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.HumanReview;

public interface HumanReviewRepository extends JpaRepository<HumanReview, UUID> {

    /** Khớp {@code UNIQUE (evidence_record_id, reviewer_id)} — nền của thao tác upsert. */
    Optional<HumanReview> findByEvidenceRecordIdAndReviewerId(UUID evidenceRecordId, UUID reviewerId);

    /** Khớp {@code idx_human_review_run}. */
    List<HumanReview> findByAnalysisRunIdAndReviewerIdOrderByUpdatedAtDesc(UUID analysisRunId, UUID reviewerId);

    /** Mọi review của một run, không lọc người — dùng cho thống kê. */
    List<HumanReview> findByAnalysisRunId(UUID analysisRunId);
}
