package vn.hust.datn.evidence.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.AnalysisRunResponse;
import vn.hust.datn.evidence.dto.EvidenceDetailResponse;
import vn.hust.datn.evidence.dto.LinkRebuildResponse;
import vn.hust.datn.evidence.mapper.AnalysisRunMapper;
import vn.hust.datn.evidence.service.EvidenceDetailService;
import vn.hust.datn.evidence.service.EvidenceQueryService;
import vn.hust.datn.evidence.service.analysis.AnalysisRunQueryService;
import vn.hust.datn.evidence.service.embedding.EmbeddingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Đường REST cho giao diện, TÁCH khỏi {@code /api/v1/tools/**} của LLM (ADR 0005).
 *
 * <h2>Frontend tuyệt đối không được gọi {@code /api/v1/tools/**}</h2>
 *
 * Chuỗi filter của nhóm tool chỉ nhận {@code X-Service-Token} và <b>không bật CORS</b>. Trình duyệt
 * gọi vào đó sẽ bị chặn ở bước preflight, và thông điệp lỗi là <i>"blocked by CORS policy"</i> chứ
 * không phải 401 — tức người debug sẽ đi sửa CORS trong khi vấn đề là chọn nhầm endpoint. Đây đúng
 * họ cạm bẫy đã ghi trong {@code CLAUDE.md}.
 *
 * <p>Ba khác biệt còn lại so với nhóm tool: camelCase thay vì snake_case, có {@code rationale}
 * (câu cho người đọc), và phân quyền theo JWT của chính người dùng thay vì service token.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceDetailService evidenceDetailService;
    private final EvidenceQueryService evidenceQueryService;
    private final EmbeddingService embeddingService;
    private final AnalysisRunMapper analysisRunMapper;
    private final AnalysisRunQueryService analysisRunQueryService;

    /**
     * Một bằng chứng kèm TOÀN BỘ liên kết của nó — nguồn dữ liệu duy nhất của Evidence Explorer.
     *
     * <p>Khoá là {@code shortId} ({@code EV-001A}) chứ không phải UUID: đó là thứ LLM viết ra
     * trong citation, nên tuần 12-13 trỏ thẳng vào URL này được mà không phải tra đổi khoá.
     *
     * <p>Mã lỗi dùng lại nguyên xi của {@code EvidenceQueryService}: 404 phân loại
     * ({@code evidence-not-found} vs {@code evidence-wrong-run} — hai câu khác nhau cho người
     * dùng), 409 {@code run-not-queryable} khi run đang chạy hoặc đã hỏng, và 404 khi run không
     * thuộc về người gọi.
     *
     * <p>Không có liên kết nào ⇒ <b>200 với các mảng rỗng</b>, không phải 404. Sự vắng mặt của
     * bằng chứng là một KẾT QUẢ — nó chính là cơ sở của verdict {@code INSUFFICIENT_EVIDENCE}.
     */
    @GetMapping("/analysis-runs/{runId}/evidence/{shortId}")
    @Operation(summary = "Chi tiết một bằng chứng kèm mọi liên kết, cho Evidence Explorer")
    public EvidenceDetailResponse detail(
            @PathVariable UUID runId, @PathVariable String shortId, @AuthenticationPrincipal CurrentUser currentUser) {
        return evidenceDetailService.detail(runId, shortId, currentUser.id());
    }

    /**
     * Sinh (lại) vector tìm-theo-nghĩa cho một run — cho run nạp TRƯỚC khi có embedding, hoặc khi
     * lần sinh tự động sau phân tích đã lỗi (hết quota…). Chỉ chủ dự án: gọi API trả phí/giới hạn
     * quota là quyết định của người sở hữu dự án.
     *
     * <p>Lỗi thì 503 kèm lý do, KHÔNG nuốt như đường tự động: ở đây có người đang bấm và chờ kết quả.
     * Lý do vẫn được ghi lên run trước khi báo lỗi (xem {@code EmbeddingService.embedRunRecordingFailure}).
     */
    @PostMapping("/analysis-runs/{runId}/embeddings")
    @Operation(summary = "Sinh lại vector tìm kiếm theo nghĩa cho một lần phân tích")
    public AnalysisRunResponse rebuildEmbeddings(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        evidenceQueryService.requireRunOwnedBy(runId, currentUser.id());
        evidenceQueryService.ensureRunQueryable(runId);
        embeddingService.embedRunRecordingFailure(runId);
        return analysisRunMapper.toResponse(analysisRunQueryService.get(runId, currentUser.id()));
    }

    /**
     * Tính lại toàn bộ liên kết của một lần phân tích, không cần chạy lại analyzer.
     *
     * <p>Tồn tại vì heuristic của linker (khớp dòng, quy ước tên, phân loại frame) CHẮC CHẮN phải
     * sửa, và sửa xong mà phải chạy lại cả sandbox thì rất tốn — nhất là với repo lớn.
     *
     * <p><b>Ranh giới của việc chạy lại</b>: nó tính lại CÁCH KHỚP, không tính lại KẾT QUẢ PARSE.
     * Sửa {@code CodeContextExtractor}, {@code StackTraceParser} hay chuẩn hoá đường dẫn thì phải
     * phân tích lại từ đầu, vì chúng sinh ra {@code evidence_record} — mà {@code short_id} của
     * evidence là thứ LLM đã trích dẫn và không được phép đổi.
     *
     * <p>Đồng bộ và trả 200, không phải 202: thao tác thuần DB, trên vulnshop chạy dưới một giây.
     * Nếu về sau repo lớn làm nó chậm thì đổi sang 202 kèm theo dõi qua {@code GET /analysis-runs/{id}}.
     *
     * <p>Là thao tác GHI nên đi đường JWT của người dùng, KHÔNG mở cho service token: năm tool
     * theo ADR 0005 đều là tool ĐỌC.
     */
    @PostMapping("/analysis-runs/{runId}/links/rebuild")
    @Operation(summary = "Tính lại liên kết bằng chứng của một lần phân tích")
    public LinkRebuildResponse rebuildLinks(@PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return evidenceDetailService.rebuildLinks(runId, currentUser.id());
    }
}
