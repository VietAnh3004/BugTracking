package vn.hust.datn.evidence.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.ReviewResponse;
import vn.hust.datn.evidence.dto.RunMetricsResponse;
import vn.hust.datn.evidence.dto.SubmitReviewRequest;
import vn.hust.datn.evidence.service.review.HumanReviewService;
import vn.hust.datn.evidence.service.review.RunMetricsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Ghi nhận quyết định của người dùng về từng cảnh báo — mắt xích cuối của luồng end-to-end.
 *
 * <p>Đây là thao tác GHI của người dùng nên chỉ đi đường JWT; năm tool theo ADR 0005 đều là tool
 * ĐỌC và đi đường riêng bằng service token.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "human-review")
@RequiredArgsConstructor
public class ReviewController {

    private final HumanReviewService humanReviewService;
    private final RunMetricsService runMetricsService;

    /**
     * Ghi hoặc cập nhật quyết định về một cảnh báo.
     *
     * <p>{@code PUT} chứ không {@code POST}: gửi lại cùng nội dung phải cho cùng kết quả, và một
     * người chỉ có đúng một quyết định cho một cảnh báo (ép bằng UNIQUE ở V9). Đổi ý thì gọi lại
     * chính endpoint này.
     *
     * <p>Trả 400 nếu {@code messageId} không phải câu trả lời mà chính người này đọc được trong
     * đúng run — xem {@code HumanReviewService.validateMessage}.
     */
    @PutMapping("/analysis-runs/{runId}/evidence/{shortId}/review")
    @Operation(summary = "Ghi quyết định của người dùng về một cảnh báo")
    public ReviewResponse submit(
            @PathVariable UUID runId,
            @PathVariable String shortId,
            @Valid @RequestBody SubmitReviewRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return humanReviewService.submit(runId, shortId, currentUser.id(), request);
    }

    /**
     * Quyết định của CHÍNH người gọi trong một lần phân tích.
     *
     * <p>Không trả về quyết định của người khác: ở tuần 16 nhiều người cùng xét một tập cảnh báo,
     * và thấy được nhãn của người trước sẽ làm hỏng tính độc lập của user study.
     */
    @GetMapping("/analysis-runs/{runId}/reviews")
    @Operation(summary = "Các quyết định của chính người gọi trong một lần phân tích")
    public List<ReviewResponse> listMine(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return humanReviewService.listMine(runId, currentUser.id());
    }

    /**
     * Chỉ số tổng hợp của một lần phân tích — nguồn dữ liệu của dashboard.
     *
     * <p>Tính trên TOÀN BỘ review của run, không chỉ của người gọi: chủ run là người chạy thực
     * nghiệm, và ở tuần 16 nhiều người cùng xét một tập cảnh báo.
     *
     * <p>Mọi tỉ lệ có thể là {@code null} — nghĩa là mẫu số bằng 0, tức "chưa đủ dữ liệu để
     * tính". Giao diện phải hiện "—", không phải {@code 0%}.
     */
    @GetMapping("/analysis-runs/{runId}/metrics")
    @Operation(summary = "Chỉ số tổng hợp của một lần phân tích")
    public RunMetricsResponse metrics(@PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return runMetricsService.compute(runId, currentUser.id());
    }
}
