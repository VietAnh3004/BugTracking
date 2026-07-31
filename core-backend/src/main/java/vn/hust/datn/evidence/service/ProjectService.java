package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.mapper.ProjectMapper;
import vn.hust.datn.evidence.repository.ProjectRepository;

/**
 * Quy tắc authorization duy nhất: tài nguyên không thuộc về bạn ⇒ 404 (qua {@link
 * ResourceNotFoundException} đã có sẵn), không phải 403 — không tạo oracle cho phép dò sự tồn
 * tại của project người khác. Mọi method đều lọc theo {@code ownerId} ngay trong câu truy vấn
 * (xem {@link ProjectRepository#findByIdAndOwnerId}), không "tìm rồi so sánh trong Java".
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository, ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public Project create(CreateProjectRequest request, UUID ownerId) {
        Project project = projectMapper.toEntity(request);
        OffsetDateTime now = OffsetDateTime.now();
        project.setOwnerId(ownerId);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Page<Project> list(Pageable pageable, UUID ownerId) {
        return projectRepository.findByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public Project get(UUID id, UUID ownerId) {
        return getOwned(id, ownerId);
    }

    /** API nội bộ cho service khác (vd {@code SnapshotService}, tuần 5-7) cần xác nhận quyền sở hữu trước khi thao tác tiếp. */
    @Transactional(readOnly = true)
    public Project getOwned(UUID projectId, UUID ownerId) {
        return projectRepository
                .findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("project", projectId));
    }
}
