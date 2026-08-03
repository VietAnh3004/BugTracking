package vn.hust.datn.evidence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.RepositorySnapshot;

public interface RepositorySnapshotRepository extends JpaRepository<RepositorySnapshot, UUID> {

    Page<RepositorySnapshot> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    /**
     * Lọc quyền sở hữu NGAY TRONG câu truy vấn, cùng khuôn với {@code ProjectRepository.findByIdAndOwnerId}
     * — snapshot không thuộc dự án của bạn thì không tồn tại (404), không phải 403.
     *
     * <p>{@code RepositorySnapshot} và {@code Project} không có quan hệ JPA (đều dùng UUID trần,
     * xem Javadoc entity), nên phải "theta join" tường minh qua {@code WHERE} thay vì navigation
     * property — giống {@code AnalysisRunRepository.findOwnerIdByRunId}.
     */
    @Query("SELECT s FROM RepositorySnapshot s, Project p "
            + "WHERE s.id = :snapshotId AND s.projectId = p.id AND p.ownerId = :ownerId")
    Optional<RepositorySnapshot> findByIdAndOwnerId(@Param("snapshotId") UUID snapshotId, @Param("ownerId") UUID ownerId);
}
