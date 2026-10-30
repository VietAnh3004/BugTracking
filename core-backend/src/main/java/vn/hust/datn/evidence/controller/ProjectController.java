package vn.hust.datn.evidence.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.dto.PageResponse;
import vn.hust.datn.evidence.dto.ProjectResponse;
import vn.hust.datn.evidence.mapper.ProjectMapper;
import vn.hust.datn.evidence.service.ProjectService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST resource thường cho UI — khác nhóm {@code /api/v1/tools/*} (RPC cho agent), xem ADR 0005.
 *
 * <p>Mọi endpoint nhận {@code @AuthenticationPrincipal CurrentUser} và forward {@code id()} xuống
 * {@link ProjectService} làm {@code ownerId} — controller không tự quyết định quyền, chỉ chuyển
 * danh tính xuống tầng service (nơi giữ quy tắc authorization duy nhất).
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        Project project = projectService.create(request, currentUser.id());
        ProjectResponse response = projectMapper.toResponse(project);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.getId())).body(response);
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return PageResponse.from(projectService.list(pageable, currentUser.id()).map(projectMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser currentUser) {
        return projectMapper.toResponse(projectService.get(id, currentUser.id()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser currentUser) {
        projectService.delete(id, currentUser.id());
    }
}
