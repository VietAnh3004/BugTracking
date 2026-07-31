package vn.hust.datn.evidence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByName(String name);

    Optional<Project> findByName(String name);

    // Lọc ở DB, không phải "tìm rồi so sánh trong Java" — không thể quên so sánh nếu điều kiện
    // nằm ngay trong câu truy vấn. Đây là quy tắc authorization duy nhất của ProjectService:
    // tài nguyên không thuộc về bạn ⇒ không xuất hiện, không phải 403.
    Page<Project> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);
}
