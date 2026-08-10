package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nhãn chuẩn của một cảnh báo — mốc so sánh cho verdict của Agent.
 *
 * <p>Bảng này DỰNG ở tuần 14 nhưng chỉ NẠP dữ liệu ở tuần 15 (kế hoạch §5). Dashboard tuần 14 có
 * sẵn ô cho "Warning assessment accuracy" và hiện "chưa có dữ liệu" cho tới lúc đó.
 *
 * <p>Khoá tổ hợp {@code (evidenceRecordId, source)}: cùng một cảnh báo có nhãn từ người gán nhãn
 * chính và từ người gán nhãn mù thứ hai, và tuần 15 cần cả hai để tính Cohen's kappa.
 */
@Entity
@Table(name = "ground_truth_label")
@IdClass(GroundTruthLabelId.class)
@Getter
@Setter
@NoArgsConstructor
public class GroundTruthLabel {

    @Id
    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroundTruthSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroundTruthVerdict label;

    /** Bắt buộc: viết được lý do nghĩa là đã đọc mã, không phải chép kết luận của Agent. */
    @Column(nullable = false)
    private String rationale;

    @Column(nullable = false)
    private String labeler;

    @Column(name = "labeled_at", nullable = false)
    private OffsetDateTime labeledAt;
}
