package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một phiên hỏi đáp với Agent, neo vào ĐÚNG MỘT {@link AnalysisRun}.
 *
 * <p>Neo vào run là điều kiện để hậu kiểm trích dẫn có nghĩa: {@code short_id} chỉ duy nhất trong
 * phạm vi một run, nên ngoài phạm vi đó "EV-01A3" là một chuỗi mơ hồ và
 * {@link ClaimCitationResolution#WRONG_RUN} không thể tồn tại.
 *
 * <p>{@code ownerId} nằm thẳng ở đây thay vì suy ngược qua run → snapshot → project: suy ngược là
 * ba lần join trên mọi request, và mỗi chỗ quên join là một lỗ rò dữ liệu.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    /** Cảnh báo đang bàn tới (dạng {@code EV-XXXX}); {@code null} nghĩa là hỏi ở phạm vi cả run. */
    @Column(name = "focus_short_id")
    private String focusShortId;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
