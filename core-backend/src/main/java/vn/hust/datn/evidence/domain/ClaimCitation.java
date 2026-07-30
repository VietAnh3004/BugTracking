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
 * Một lượt LLM trích dẫn một Evidence ID trong một {@link Claim}.
 *
 * <p>Không xoá dòng nào — là mẫu số của Unsupported Claim Rate và Evidence Citation Accuracy.
 * {@code citedShortIdRaw} giữ NGUYÊN VĂN chuỗi LLM viết ra, kể cả khi ID đó không tồn tại.
 */
@Entity
@Table(name = "claim_citation")
@Getter
@Setter
@NoArgsConstructor
public class ClaimCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "cited_short_id_raw", nullable = false)
    private String citedShortIdRaw;

    @Column(name = "evidence_record_id")
    private UUID evidenceRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimCitationResolution resolution;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
