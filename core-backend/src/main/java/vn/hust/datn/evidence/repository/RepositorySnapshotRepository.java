package vn.hust.datn.evidence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.RepositorySnapshot;

public interface RepositorySnapshotRepository extends JpaRepository<RepositorySnapshot, UUID> {}
