package vn.hust.datn.evidence.controller;

import java.io.IOException;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.GroundTruthImportResponse;
import vn.hust.datn.evidence.dto.GroundTruthSheetResponse;
import vn.hust.datn.evidence.dto.SaveGroundTruthRequest;
import vn.hust.datn.evidence.exception.InvalidGroundTruthException;
import vn.hust.datn.evidence.service.review.GroundTruthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Gán nhãn ground truth — đáp án đúng/sai cho từng cảnh báo, dùng để chấm độ chính xác của Agent
 * và tính Cohen's kappa giữa hai người gán.
 *
 * <p>Đọc bảng và ghi nhãn: chủ dự án và thành viên (mỗi bên ghi vào nguồn của mình). Xuất/nạp
 * file: chỉ chủ dự án.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "ground-truth")
@RequiredArgsConstructor
public class GroundTruthController {

    /** Trần kích thước file nhãn: vài trăm mục chỉ vài chục KB, 1 MB là thừa sức. */
    private static final long MAX_IMPORT_BYTES = 1024 * 1024;

    private final GroundTruthService groundTruthService;

    @GetMapping("/analysis-runs/{runId}/ground-truth")
    @Operation(summary = "Bảng gán nhãn ground truth của một lần phân tích, lọc theo vai trò người gọi")
    public GroundTruthSheetResponse sheet(@PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return groundTruthService.sheet(runId, currentUser.id());
    }

    @PutMapping("/analysis-runs/{runId}/evidence/{shortId}/ground-truth")
    @Operation(summary = "Gán hoặc sửa nhãn ground truth của người gọi cho một cảnh báo")
    public GroundTruthSheetResponse.GroundTruthLabelView save(
            @PathVariable UUID runId,
            @PathVariable String shortId,
            @Valid @RequestBody SaveGroundTruthRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return groundTruthService.save(runId, shortId, currentUser.id(), request);
    }

    @DeleteMapping("/analysis-runs/{runId}/evidence/{shortId}/ground-truth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá nhãn ground truth của người gọi trên một cảnh báo")
    public void delete(
            @PathVariable UUID runId, @PathVariable String shortId, @AuthenticationPrincipal CurrentUser currentUser) {
        groundTruthService.delete(runId, shortId, currentUser.id());
    }

    @GetMapping(value = "/analysis-runs/{runId}/ground-truth/export.yaml", produces = "application/yaml")
    @Operation(summary = "Xuất mọi nhãn của lần phân tích ra YAML (chỉ chủ dự án)")
    public ResponseEntity<byte[]> export(@PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ground-truth-" + runId + ".yaml\"")
                .body(groundTruthService.exportYaml(runId, currentUser.id()));
    }

    @PostMapping(value = "/analysis-runs/{runId}/ground-truth/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nạp nhãn từ file YAML vào lần phân tích (chỉ chủ dự án)")
    public GroundTruthImportResponse importLabels(
            @PathVariable UUID runId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser currentUser) {
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new InvalidGroundTruthException("File nhan qua lon (toi da 1 MB).");
        }
        try {
            return groundTruthService.importYaml(runId, currentUser.id(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidGroundTruthException("Khong doc duoc file tai len: " + e.getMessage());
        }
    }
}
