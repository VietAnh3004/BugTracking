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

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository, ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public Project create(CreateProjectRequest request) {
        Project project = projectMapper.toEntity(request);
        OffsetDateTime now = OffsetDateTime.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Page<Project> list(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Project get(UUID id) {
        return projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("project", id));
    }
}
