package vn.hust.datn.evidence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.EvidenceLink;

public interface EvidenceLinkRepository extends JpaRepository<EvidenceLink, UUID> {}
