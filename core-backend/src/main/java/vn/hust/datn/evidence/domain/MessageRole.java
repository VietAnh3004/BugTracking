package vn.hust.datn.evidence.domain;

/**
 * Vai trò của một lượt trong hội thoại.
 *
 * <p>Chỉ hai giá trị. Không có {@code SYSTEM}: system prompt thuộc về {@code llm_trace} (nó đổi
 * theo phiên bản prompt) chứ không phải hội thoại người dùng nhìn thấy. Cũng không có
 * {@code TOOL}: lượt gọi tool nằm ở {@code llm_trace.tool_calls}, đưa vào đây sẽ khiến
 * {@code ordinal} của hội thoại nhảy cóc mỗi khi đổi số vòng lặp.
 */
public enum MessageRole {
    USER,
    ASSISTANT
}
