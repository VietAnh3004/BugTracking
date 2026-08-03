package vn.hust.datn.evidence.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.dto.FileContentResponse;
import vn.hust.datn.evidence.dto.PageResponse;
import vn.hust.datn.evidence.dto.SnapshotFileResponse;
import vn.hust.datn.evidence.dto.SnapshotResponse;
import vn.hust.datn.evidence.mapper.SnapshotFileMapper;
import vn.hust.datn.evidence.mapper.SnapshotMapper;
import vn.hust.datn.evidence.service.SnapshotQueryService;
import vn.hust.datn.evidence.service.SnapshotService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST resource thường cho UI, cùng nhóm với {@link ProjectController} (khác {@code /api/v1/tools/*}
 * dành cho agent, xem ADR 0005). Upload xử lý đồng bộ ở đợt này — client chờ response trả về
 * snapshot {@code READY} hoặc lỗi, chưa polling bất đồng bộ.
 *
 * <p>{@code @RequestMapping("/api/v1")} ở mức class với đường dẫn đầy đủ trên từng method: nhóm
 * endpoint này có hai dạng URL khác gốc nhau — tạo/liệt kê thì lồng trong project
 * ({@code /projects/{projectId}/snapshots}), còn đọc thì phẳng theo snapshot
 * ({@code /snapshots/{id}/...}) vì snapshot id đã đủ định danh và URL ngắn hơn cho deep-link.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final SnapshotQueryService snapshotQueryService;
    private final SnapshotMapper snapshotMapper;
    private final SnapshotFileMapper snapshotFileMapper;

    @PostMapping("/projects/{projectId}/snapshots")
    public ResponseEntity<SnapshotResponse> upload(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser currentUser) {
        RepositorySnapshot snapshot = snapshotService.upload(projectId, currentUser.id(), file);
        SnapshotResponse response = snapshotMapper.toResponse(snapshot);
        return ResponseEntity.created(URI.create("/api/v1/snapshots/" + snapshot.getId()))
                .body(response);
    }

    @GetMapping("/projects/{projectId}/snapshots")
    public PageResponse<SnapshotResponse> listByProject(
            @PathVariable UUID projectId, Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return PageResponse.from(snapshotQueryService
                .listByProject(projectId, currentUser.id(), pageable)
                .map(snapshotMapper::toResponse));
    }

    @GetMapping("/snapshots/{snapshotId}")
    public SnapshotResponse get(@PathVariable UUID snapshotId, @AuthenticationPrincipal CurrentUser currentUser) {
        return snapshotMapper.toResponse(snapshotQueryService.get(snapshotId, currentUser.id()));
    }

    /** Danh sách PHẲNG toàn snapshot — FE tự dựng cây, xem Javadoc {@link SnapshotFileResponse}. */
    @GetMapping("/snapshots/{snapshotId}/files")
    public List<SnapshotFileResponse> listFiles(
            @PathVariable UUID snapshotId, @AuthenticationPrincipal CurrentUser currentUser) {
        return snapshotFileMapper.toResponses(snapshotQueryService.listFiles(snapshotId, currentUser.id()));
    }

    /**
     * {@code path} là QUERY PARAM chứ không phải path variable wildcard: đường dẫn file chứa dấu
     * {@code /}, dùng wildcard buộc phải tự giải mã phần đuôi URL và mở lại đúng lớp lỗi
     * encoding/traversal mà {@code ZipExtractor} vừa chặn lúc upload. Tra cứu bằng
     * {@code UNIQUE (snapshot_id, path)} nên chỉ khớp chính xác, không có so khớp mờ nào.
     */
    @GetMapping("/snapshots/{snapshotId}/file")
    public FileContentResponse readFile(
            @PathVariable UUID snapshotId,
            @RequestParam String path,
            @RequestParam(required = false) Integer fromLine,
            @RequestParam(required = false) Integer toLine,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return snapshotQueryService.readFile(snapshotId, currentUser.id(), path, fromLine, toLine);
    }
}
