package vn.hust.datn.evidence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.EvidenceRecord;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, UUID> {}
