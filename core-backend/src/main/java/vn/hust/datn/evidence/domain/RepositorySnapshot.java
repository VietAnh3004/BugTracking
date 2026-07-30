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
 * Entity cho bảng {@code repository_snapshot} — có từ V1__baseline.sql (tuần 1) nhưng chưa có
 * entity Java cho tới tuần 4. Thêm sớm để tuần 4 chèn được dữ liệu test qua repository thay vì
 * {@code JdbcTemplate} SQL thô, và để tuần 5-7 (project/repo CRUD) không phải viết lại fixture.
 *
 * <p>Không map {@code @ManyToOne} sang {@link Project}: chưa có tính năng nào cần duyệt quan hệ
 * hai chiều, giữ UUID trần như các entity khác của tuần 3 (xem {@link AnalysisRun#getSnapshotId()}).
 *
 * <p>{@code status/sourceType/fileCount/totalSizeBytes/errorMessage} thêm ở V3 (tuần 5-7) cho
 * luồng upload zip: xử lý đồng bộ ở đợt này, nhưng {@code status} có sẵn để chuyển sang bất đồng
 * bộ sau mà không phải migration lại.
 */
@Entity
@Table(name = "repository_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class RepositorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnapshotStatus status = SnapshotStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SnapshotSourceType sourceType = SnapshotSourceType.ZIP_UPLOAD;

    @Column(name = "file_count", nullable = false)
    private int fileCount = 0;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
