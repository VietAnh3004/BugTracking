package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Mở một phiên hỏi đáp.
 *
 * <p>Cả hai trường đều tuỳ chọn: hỏi ở phạm vi cả run là hợp lệ, và tiêu đề mặc định suy từ
 * {@code focusShortId} để người dùng không phải đặt tên trước khi biết mình sẽ hỏi gì.
 */
@Schema(description = "Mở một phiên hỏi đáp mới")
public record CreateConversationRequest(
        // Kiểm khuôn dạng ở đây chứ không trong service: sai định dạng là lỗi đầu vào, và
        // @Pattern cho ra 400 'validation-failed' đúng khuôn RFC 7807 mà không cần exception mới.
        @Schema(description = "Cảnh báo muốn bàn tới, dạng EV-XXXX")
                @Pattern(regexp = "^EV-[0-9A-Fa-f]{4}$", message = "phải có dạng EV-XXXX") String focusShortId,
        @Schema(description = "Tiêu đề hiển thị; bỏ trống thì sinh tự động") @Size(max = 200) String title) {}
