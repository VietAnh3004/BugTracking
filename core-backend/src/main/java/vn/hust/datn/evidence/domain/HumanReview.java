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
 * Một người phán xét MỘT cảnh báo.
 *
 * <p>Neo vào cảnh báo chứ không vào câu trả lời của Agent (ADR 0011): user study ở tuần 16 chia
 * người dùng làm hai nhóm, và nhóm không dùng Agent sẽ không có chỗ nào để ghi nếu bảng này neo
 * vào {@link Message}.
 */
@Entity
@Table(name = "human_review")
@Getter
@Setter
@NoArgsConstructor
public class HumanReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewDecision decision;

    /**
     * Câu trả lời của Agent người này đã đọc trước khi quyết; {@code null} nghĩa là không dùng
     * Agent.
     *
     * <p>Đây là <b>biến thực nghiệm</b> của user study, không phải metadata. Backend kiểm ba điều
     * kiện trước khi ghi (tồn tại, thuộc hội thoại của chính người review, cùng run) — nhận bừa
     * từ client sẽ làm nhiễm nhóm đối chứng và hỏng đúng dữ liệu tuần 16.
     */
    @Column(name = "message_id")
    private UUID messageId;

    @Column
    private String note;

    /**
     * Client tự đo nên chỉ tham khảo. KHÔNG phải "Time to understand" của kế hoạch §6 — cái đó đo
     * trong phiên có người quan sát.
     */
    @Column(name = "seconds_spent")
    private Integer secondsSpent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
