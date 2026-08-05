package vn.hust.datn.evidence.controller;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.dto.AnalysisRunResponse;
import vn.hust.datn.evidence.mapper.AnalysisRunMapper;
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
    private final AnalysisRunMapper analysisRunMapper;

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
