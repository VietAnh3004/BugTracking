package vn.hust.datn.evidence.controller;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.AnalysisRunResponse;
import vn.hust.datn.evidence.dto.PageResponse;
import vn.hust.datn.evidence.dto.WarningView;
import vn.hust.datn.evidence.mapper.AnalysisRunMapper;
import vn.hust.datn.evidence.service.analysis.AnalysisRunQueryService;
import vn.hust.datn.evidence.service.analysis.AnalysisRunService;
import vn.hust.datn.evidence.service.analysis.InvalidArtifactException;
import vn.hust.datn.evidence.service.analysis.NamedArtifact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Nạp artifact analyzer cho một snapshot.
 *
 * <p><b>Endpoint này KHÔNG phải giải pháp tạm.</b> Ở giai đoạn Docker sandbox nó vẫn tồn tại song
 * song: sandbox chỉ thay đổi <i>nguồn</i> của {@code byte[]}, không đổi một dòng nào của đường
 * parse → materialize. Nó cũng là đường demo được ngay bằng {@code docs/samples/vulnshop/}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "analysis-runs")
@RequiredArgsConstructor
public class AnalysisRunController {

    private final AnalysisRunService analysisRunService;
    private final AnalysisRunQueryService analysisRunQueryService;
    private final AnalysisRunMapper analysisRunMapper;

    @GetMapping("/snapshots/{snapshotId}/analysis-runs")
    @Operation(summary = "Các lần phân tích của một snapshot, mới nhất trước")
    public PageResponse<AnalysisRunResponse> listBySnapshot(
            @PathVariable UUID snapshotId, Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return PageResponse.from(analysisRunQueryService
                .listBySnapshot(snapshotId, currentUser.id(), pageable)
                .map(analysisRunMapper::toResponse));
    }

    /** Đọc được cả run {@code RUNNING}/{@code FAILED} — đây chính là chỗ FE lấy lý do hỏng. */
    @GetMapping("/analysis-runs/{runId}")
    @Operation(summary = "Một lần phân tích, kể cả khi đang chạy hoặc đã hỏng")
    public AnalysisRunResponse get(@PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return analysisRunMapper.toResponse(analysisRunQueryService.get(runId, currentUser.id()));
    }

    /**
     * Danh sách cảnh báo, đã sắp theo mức nghiêm trọng ở tầng SQL.
     *
     * <p>KHÔNG nhận tham số {@code sort}: thứ tự cố định theo {@code warning.severity_rank}. Cho
     * client tự sort sẽ mở lại đúng cái bẫy mà cột generated đó sinh ra để chặn —
     * {@code severity_normalized} là TEXT nên sắp chữ cái ra HIGH, INFO, LOW, MEDIUM.
     */
    @GetMapping("/analysis-runs/{runId}/warnings")
    @Operation(summary = "Cảnh báo tĩnh của một lần phân tích, sắp theo mức nghiêm trọng")
    public PageResponse<WarningView> listWarnings(
            @PathVariable UUID runId,
            @RequestParam(required = false) WarningTool tool,
            @RequestParam(required = false) WarningSeverity severity,
            @RequestParam(required = false) String filePath,
            Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return PageResponse.from(
                analysisRunQueryService.listWarnings(runId, currentUser.id(), tool, severity, filePath, pageable));
    }

    /**
     * MỘT trường lặp {@code artifacts} thay vì bốn trường có tên (spotbugs/semgrep/surefire/jacoco).
     *
     * <p>Việc chọn parser hoàn toàn dựa vào NỘI DUNG file, nên bốn trường có tên sẽ là một lời hứa
     * sai: người dùng gán nhầm tên trường vẫn được xử lý đúng, và một API tự mâu thuẫn như vậy khó
     * dùng hơn là dễ. Danh sách này nhận cả {@code TEST-*.xml} lẻ lẫn một zip báo cáo Surefire —
     * zip được bung ra ở tầng service.
     *
     * <p>Trả về 201 kể cả khi run ra {@code FAILED}: hàng {@code analysis_run} ĐÃ được tạo và đọc
     * được qua {@code GET /analysis-runs/{id}}, nên 201 mô tả đúng thực tế. Lỗi 4xx dành cho những
     * gì bị từ chối TRƯỚC khi có hàng nào.
     */
    @PostMapping(value = "/snapshots/{snapshotId}/analysis-runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nạp artifact analyzer và tạo một lần phân tích")
    public ResponseEntity<AnalysisRunResponse> ingest(
            @PathVariable UUID snapshotId,
            @RequestParam("artifacts") List<MultipartFile> artifacts,
            @AuthenticationPrincipal CurrentUser currentUser) {
        AnalysisRun run = analysisRunService.ingest(snapshotId, currentUser.id(), toNamedArtifacts(artifacts));
        return ResponseEntity.created(URI.create("/api/v1/analysis-runs/" + run.getId()))
                .body(analysisRunMapper.toResponse(run));
    }

    /**
     * Chạy analyzer trong sandbox — BẤT ĐỒNG BỘ, trả 202 kèm run {@code RUNNING}.
     *
     * <p>202 chứ không phải 201: công việc mới được CHẤP NHẬN chứ chưa xong. FE theo dõi bằng
     * {@code GET /analysis-runs/{id}} (polling 2 giây) — endpoint đó cố ý đọc được cả run
     * {@code RUNNING} lẫn {@code FAILED}.
     *
     * <p>503 khi sandbox tắt hoặc daemon không trả lời, 409 khi cây mã nguồn không phân tích được
     * — cả hai ném TRƯỚC khi tạo hàng nào.
     */
    @PostMapping("/snapshots/{snapshotId}/analysis-runs/sandbox")
    @Operation(summary = "Chạy analyzer trong Docker sandbox trên mã nguồn đã upload")
    public ResponseEntity<AnalysisRunResponse> runInSandbox(
            @PathVariable UUID snapshotId, @AuthenticationPrincipal CurrentUser currentUser) {
        AnalysisRun run = analysisRunService.startSandboxRun(snapshotId, currentUser.id());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analysis-runs/" + run.getId()))
                .body(analysisRunMapper.toResponse(run));
    }

    private List<NamedArtifact> toNamedArtifacts(List<MultipartFile> files) {
        List<NamedArtifact> artifacts = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                // Bỏ qua phần rỗng thay vì để nó rơi xuống "khong nhan dang duoc": một số client
                // gửi phần trống cho ô chọn file chưa điền.
                continue;
            }
            artifacts.add(new NamedArtifact(file.getOriginalFilename(), readBytes(file)));
        }
        return artifacts;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidArtifactException(
                    "Khong doc duoc noi dung phan '%s'".formatted(file.getOriginalFilename()), e);
        }
    }
}
