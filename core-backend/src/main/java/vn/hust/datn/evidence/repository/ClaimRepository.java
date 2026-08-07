package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.Claim;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    // Khớp idx_claim_message (message_id, ordinal) thêm ở V8.
    List<Claim> findByMessageIdOrderByOrdinal(UUID messageId);
}
