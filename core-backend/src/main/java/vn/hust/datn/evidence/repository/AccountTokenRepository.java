package vn.hust.datn.evidence.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.AccountToken;
import vn.hust.datn.evidence.domain.AccountTokenPurpose;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    Optional<AccountToken> findByTokenHash(String tokenHash);

    Optional<AccountToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, AccountTokenPurpose purpose);

    /**
     * Dùng token: {@code usedAt IS NULL} trong WHERE biến kiểm-rồi-ghi thành MỘT thao tác nguyên
     * tử — hai lần bấm cùng một link song song thì đúng một lần thành công (như
     * {@link RefreshTokenRepository#markRotated}).
     */
    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /** Gửi link mới thì link cũ cùng mục đích hết hiệu lực — chỉ email mới nhất là dùng được. */
    @Modifying
    @Query(
            "UPDATE AccountToken t SET t.usedAt = :now "
                    + "WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidatePending(
            @Param("userId") UUID userId, @Param("purpose") AccountTokenPurpose purpose, @Param("now") OffsetDateTime now);
}
