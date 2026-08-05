package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * Tổng số vị trí file:line mà parser thử đối chiếu với {@code snapshot_file.path}. Frame
     * JDK/JUnit KHÔNG được tính vào — nếu tính, tỉ lệ thất bại của một run hoàn toàn đúng vẫn là
     * ~84% và bộ đếm mất sạch giá trị cảnh báo.
     */
    @Column(name = "location_count", nullable = false)
    private int locationCount = 0;

    /**
     * Số vị trí không đối chiếu được. ADR 0004 gọi đích danh chuẩn hoá đường dẫn là heuristic có
     * thể hỏng im lặng thành 0 evidence_link; cột này ép nó hiện thành số, và
     * {@code ck_run_paths_not_all_unresolved} chặn một run SUCCEEDED khi hỏng 100%.
     */
    @Column(name = "unresolved_location_count", nullable = false)
    private int unresolvedLocationCount = 0;

    /**
     * Phiên bản tool đọc từ CHÍNH artifact. Khoá {@code jacoco} vắng mặt là bình thường —
     * jacoco.xml không khai version — chứ không phải thiếu dữ liệu.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_versions")
    private Map<String, Object> toolVersions;

    /** Xem {@link ArtifactSource}. Mặc định {@code MANUAL_UPLOAD} khớp DEFAULT của cột. */
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_source", nullable = false)
    private ArtifactSource artifactSource = ArtifactSource.MANUAL_UPLOAD;

    /**
     * Digest của image analyzer ĐÃ THỰC SỰ CHẠY, không phải tag: tag {@code :latest} trỏ tới image
     * khác nhau sau mỗi lần dựng lại, nên ghi tag thì tuần 15-16 chỉ nói được "cùng một cái tên"
     * chứ không nói được "cùng một bộ analyzer".
     */
    @Column(name = "analyzer_image_digest")
    private String analyzerImageDigest;

    /** Thư mục chứa {@code pom.xml} nông nhất trong cây đã upload. */
    @Column(name = "source_root")
    private String sourceRoot;

    /**
     * NULL cho run nạp thủ công — ở đó không có canary để mà kiểm, và điều đó khác hẳn "có canary
     * nhưng nó đỏ". {@code ck_run_sandbox_needs_canary} chặn một run sandbox SUCCEEDED khi cột này
     * không phải TRUE.
     */
    @Column(name = "canary_ok")
    private Boolean canaryOk;

    /** Số finding của mã canary, đã LOẠI khỏi {@link #findingCount}. */
    @Column(name = "canary_finding_count", nullable = false)
    private int canaryFindingCount = 0;

    @Column(name = "sandbox_duration_ms")
    private Long sandboxDurationMs;
}
