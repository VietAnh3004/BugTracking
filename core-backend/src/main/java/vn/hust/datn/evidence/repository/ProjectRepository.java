package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.ProjectTier;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByName(String name);

    Optional<Project> findByName(String name);

    // Lọc ở DB, không phải "tìm rồi so sánh trong Java" — không thể quên so sánh nếu điều kiện
    // nằm ngay trong câu truy vấn. Đây là quy tắc authorization duy nhất của ProjectService:
    // tài nguyên không thuộc về bạn ⇒ không xuất hiện, không phải 403.
    Page<Project> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Mọi dự án một người SỞ HỮU — dùng khi admin xoá tài khoản (không phân trang). */
    List<Project> findAllByOwnerId(UUID ownerId);

    /**
     * Dự án người dùng ĐỌC được: sở hữu, hoặc là thành viên (V10). Cùng nguyên tắc với
     * {@link #findByIdAndOwnerId}: điều kiện quyền nằm trong câu truy vấn, không đọc được thì
     * không tồn tại (404), không phải 403.
     */
    String READABLE_BY =
            "(p.ownerId = :userId OR EXISTS (SELECT 1 FROM ProjectMember m WHERE m.projectId = p.id AND m.userId = :userId))";

    @Query(value = "SELECT p FROM Project p WHERE " + READABLE_BY,
            countQuery = "SELECT count(p) FROM Project p WHERE " + READABLE_BY)
    Page<Project> findReadable(@Param("userId") UUID userId, Pageable pageable);

    /** Hạng của dự án chứa một run — đọc ở mỗi câu hỏi Agent để admin đổi hạng có hiệu lực ngay. */
    @Query("SELECT p.tier FROM Project p, RepositorySnapshot s, AnalysisRun r "
            + "WHERE r.id = :runId AND r.snapshotId = s.id AND s.projectId = p.id")
    Optional<ProjectTier> findTierByRunId(@Param("runId") UUID runId);

    @Query("SELECT p FROM Project p WHERE p.id = :id AND " + READABLE_BY)
    Optional<Project> findReadableById(@Param("id") UUID id, @Param("userId") UUID userId);
}
