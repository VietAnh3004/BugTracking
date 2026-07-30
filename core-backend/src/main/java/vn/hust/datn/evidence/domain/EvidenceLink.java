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

/** Quan hệ giữa hai {@link EvidenceRecord}. {@code method} ghi lại độ tin cậy của cách suy ra quan hệ. */
@Entity
@Table(name = "evidence_link")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_evidence_id", nullable = false)
    private UUID fromEvidenceId;

    @Column(name = "to_evidence_id", nullable = false)
    private UUID toEvidenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceLinkRelation relation;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal weight = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceLinkMethod method;

    private String rationale;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
