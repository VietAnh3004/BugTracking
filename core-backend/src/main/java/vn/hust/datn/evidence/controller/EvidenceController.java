package vn.hust.datn.evidence.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.EvidenceDetailResponse;
import vn.hust.datn.evidence.service.EvidenceDetailService;

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
}
