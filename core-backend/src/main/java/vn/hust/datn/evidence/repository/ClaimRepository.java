package vn.hust.datn.evidence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.Claim;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {}
