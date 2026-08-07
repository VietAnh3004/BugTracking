package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.ClaimCitation;

public interface ClaimCitationRepository extends JpaRepository<ClaimCitation, UUID> {

    // Khớp idx_claim_citation_claim (V2). Không lọc theo resolution: giao diện hiện CẢ những
    // trích dẫn hỏng, vì chúng là mẫu số của Evidence Citation Accuracy.
    List<ClaimCitation> findByClaimId(UUID claimId);
}
