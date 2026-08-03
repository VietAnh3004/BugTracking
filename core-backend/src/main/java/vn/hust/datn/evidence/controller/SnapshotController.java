package vn.hust.datn.evidence.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.dto.SnapshotResponse;
import vn.hust.datn.evidence.mapper.SnapshotMapper;
import vn.hust.datn.evidence.service.SnapshotService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST resource thường cho UI, cùng nhóm với {@link ProjectController} (khác {@code /api/v1/tools/*}
 * dành cho agent, xem ADR 0005). Xử lý upload đồng bộ ở đợt này — client chờ response trả về
 * snapshot {@code READY} hoặc {@code FAILED}, chưa polling bất đồng bộ.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/snapshots")
@Tag(name = "snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final SnapshotMapper snapshotMapper;

    @PostMapping
    public ResponseEntity<SnapshotResponse> upload(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser currentUser) {
        RepositorySnapshot snapshot = snapshotService.upload(projectId, currentUser.id(), file);
        SnapshotResponse response = snapshotMapper.toResponse(snapshot);
        return ResponseEntity.created(URI.create("/api/v1/snapshots/" + snapshot.getId()))
                .body(response);
    }
}
