package vn.hust.datn.evidence.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.ExperimentStatusResponse;
import vn.hust.datn.evidence.service.experiment.ExperimentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Thực nghiệm 3 cấu hình (LLM-Code / LLM-Static / Evidence-Agent) trên một lần phân tích. Chỉ chủ
 * dự án — cùng nhóm quyền với dashboard và xuất báo cáo; người ngoài nhận 404.
 */
@RestController
@RequestMapping("/api/v1/analysis-runs/{runId}/experiment")
@Tag(name = "experiment")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;

    @GetMapping
    @Operation(summary = "Tiến độ và chỉ số của thực nghiệm 3 cấu hình")
    public ExperimentStatusResponse status(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return experimentService.status(runId, currentUser.id());
    }

    /**
     * Chạy, hoặc chạy tiếp từ ô dở: ô đã thành công được bỏ qua, ô hỏng được chạy lại. Trả về ngay;
     * các ô chạy trên luồng nền. 409 nếu đang chạy.
     */
    @PostMapping
    @Operation(summary = "Chạy hoặc chạy tiếp thực nghiệm 3 cấu hình")
    public ExperimentStatusResponse start(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return experimentService.start(runId, currentUser.id());
    }

    @PostMapping("/stop")
    @Operation(summary = "Dừng thực nghiệm sau ô đang chạy")
    public ExperimentStatusResponse stop(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return experimentService.stop(runId, currentUser.id());
    }

    /** Mỗi ô một dòng. {@code byte[]} để BOM UTF-8 đi nguyên vẹn ra dây (xem ReviewController). */
    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Xuất kết quả thực nghiệm ra CSV")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + experimentService.csvFileName(runId) + "\"")
                .body(experimentService.csv(runId, currentUser.id()));
    }
}
