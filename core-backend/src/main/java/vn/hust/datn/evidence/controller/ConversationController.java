package vn.hust.datn.evidence.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.AskRequest;
import vn.hust.datn.evidence.dto.ChatMessageResponse;
import vn.hust.datn.evidence.dto.ConversationResponse;
import vn.hust.datn.evidence.dto.CreateConversationRequest;
import vn.hust.datn.evidence.service.ConversationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Giao diện hội thoại với Agent — đường REST/SSE cho trình duyệt.
 *
 * <h2>Frontend gọi vào đây, KHÔNG gọi thẳng agent-service</h2>
 *
 * agent-service không có xác thực người dùng, không có CORS, và không biết ai sở hữu run nào.
 * Quan trọng hơn: câu trả lời của nó chưa qua hậu kiểm trích dẫn. Cho trình duyệt gọi thẳng vào
 * đó nghĩa là người dùng đọc kết luận trước khi nó bị hạ cấp — và bước hậu kiểm thành trang trí
 * (ADR 0010).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/analysis-runs/{runId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mở một phiên hỏi đáp về một lần phân tích")
    public ConversationResponse create(
            @PathVariable UUID runId,
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return conversationService.create(runId, currentUser.id(), request);
    }

    @GetMapping("/analysis-runs/{runId}/conversations")
    @Operation(summary = "Các phiên hỏi đáp của một lần phân tích")
    public List<ConversationResponse> list(
            @PathVariable UUID runId, @AuthenticationPrincipal CurrentUser currentUser) {
        return conversationService.list(runId, currentUser.id());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Toàn bộ lượt của một phiên, đã qua hậu kiểm trích dẫn")
    public List<ChatMessageResponse> messages(
            @PathVariable UUID conversationId, @AuthenticationPrincipal CurrentUser currentUser) {
        return conversationService.messages(conversationId, currentUser.id());
    }

    /**
     * Hỏi một câu và nhận tiến trình qua SSE.
     *
     * <p>Bốn loại sự kiện: {@code status}, {@code tool_call}, {@code tool_result} là tiến trình
     * truy xuất bằng chứng, forward nguyên văn từ agent-service; {@code answer} là câu trả lời
     * <b>đã qua hậu kiểm</b> và đã ghi xuống DB. Lỗi ra {@code error}.
     *
     * <p>Không stream từng token của câu trả lời: đầu ra bị ép theo cấu trúc nên JSON dở dang
     * chẳng đọc được, mà tệ hơn là nó hiện ra những Evidence ID chưa kiểm.
     *
     * <p>Trả {@code SseEmitter} nên Spring nhả luồng của servlet ngay; công việc chạy trên pool
     * {@code agentExecutor}.
     */
    @PostMapping(value = "/conversations/{conversationId}/messages", produces = "text/event-stream")
    @Operation(summary = "Hỏi Agent một câu, nhận tiến trình và câu trả lời qua SSE")
    public SseEmitter ask(
            @PathVariable UUID conversationId,
            @Valid @RequestBody AskRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return conversationService.ask(conversationId, currentUser.id(), request.question());
    }
}
