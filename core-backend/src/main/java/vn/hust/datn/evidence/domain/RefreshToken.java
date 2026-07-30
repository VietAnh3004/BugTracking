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
 * Refresh token quay vòng (V3). Không map {@code @ManyToOne} tới {@link User} — giữ UUID trần
 * như các entity khác của dự án (xem {@link AnalysisRun#getSnapshotId()}).
 *
 * <p>{@code tokenHash} là SHA-256 hex của token gốc, không phải BCrypt: token là 256 bit ngẫu
 * nhiên nên không cần hàm băm chậm, và cần tra cứu trực tiếp bằng {@code WHERE token_hash = ?}
 * (BCrypt salt ngẫu nhiên sẽ không cho phép việc này).
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason")
    private RefreshTokenRevokedReason revokedReason;

    @Column(name = "user_agent")
    private String userAgent;
}
