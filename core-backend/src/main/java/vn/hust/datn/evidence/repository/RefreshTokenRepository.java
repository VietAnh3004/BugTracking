package vn.hust.datn.evidence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Xoay token: {@code used_at IS NULL} trong WHERE là phần quan trọng nhất — biến
     * kiểm-tra-rồi-cập-nhật thành một thao tác nguyên tử của DB. Hai request refresh song song
     * dùng cùng token thì chỉ đúng một câu UPDATE ảnh hưởng 1 hàng; câu còn lại trả về 0 và phải
     * được service xử lý như tái sử dụng.
     */
    @Modifying
    @Query(
            "UPDATE RefreshToken t SET t.usedAt = CURRENT_TIMESTAMP, t.revokedAt = CURRENT_TIMESTAMP, "
                    + "t.revokedReason = vn.hust.datn.evidence.domain.RefreshTokenRevokedReason.ROTATED "
                    + "WHERE t.id = :id AND t.usedAt IS NULL")
    int markRotated(@Param("id") UUID id);

    @Modifying
    @Query(
            "UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP, t.revokedReason = :reason "
                    + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("reason") vn.hust.datn.evidence.domain.RefreshTokenRevokedReason reason);
}
