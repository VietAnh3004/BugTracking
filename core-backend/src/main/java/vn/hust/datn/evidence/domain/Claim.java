package vn.hust.datn.evidence.domain;

import java.math.BigDecimal;
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
 * Một nhận định của Agent. Không xoá khi verdict bị CitationValidator hạ xuống
 * INSUFFICIENT_EVIDENCE — cần giữ để tính Unsupported Claim Rate.
 *
 * <p>{@code messageId} CHƯA có ràng buộc FK ở tầng DB: bảng {@code message}/{@code conversation}
 * thuộc phạm vi tuần 12-13 (agent service + chat). Ràng buộc sẽ thêm bằng migration riêng khi đó.
 */
@Entity
@Table(name = "claim")
@Getter
@Setter
@NoArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimVerdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "downgraded_from_verdict")
    private ClaimVerdict downgradedFromVerdict;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
