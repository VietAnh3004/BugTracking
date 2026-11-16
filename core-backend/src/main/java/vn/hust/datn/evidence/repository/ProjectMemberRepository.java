package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.ProjectMember;
import vn.hust.datn.evidence.domain.ProjectMemberId;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findByProjectIdOrderByAddedAtAsc(UUID projectId);
}
