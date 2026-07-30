package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một lần chạy pipeline phân tích trên một repository_snapshot.
 *
 * <p>Không map snapshot_id thành quan hệ {@code @ManyToOne} tới RepositorySnapshot: entity đó
 * ngoài phạm vi tuần 3 (không có tính năng nào đụng tới nó), nên giữ dạng UUID trần — schema DB
 * vẫn có FK ràng buộc đầy đủ (xem V2__evidence_model.sql), chỉ tầng entity là chưa mô hình hoá
 * quan hệ hai chiều.
 */
@Entity
@Table(name = "analysis_run")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisRunStatus status = AnalysisRunStatus.RUNNING;

    @Column(name = "finding_count", nullable = false)
    private int findingCount = 0;

    @Column(name = "clean_ack_note")
    private String cleanAckNote;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
