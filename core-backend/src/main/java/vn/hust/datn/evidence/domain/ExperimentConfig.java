package vn.hust.datn.evidence.domain;

import java.util.Locale;

/**
 * Ba cấu hình của thực nghiệm (kế hoạch §6, {@code docs/thuc-nghiem.md}). Cùng model, cùng câu hỏi,
 * cùng lược đồ trả lời; chỉ khác lượng thông tin model được thấy.
 *
 * <p>Thứ tự khai báo là thứ tự chạy trong một cảnh báo và thứ tự cột trên giao diện.
 */
public enum ExperimentConfig {
    /** Chỉ đoạn mã quanh dòng bị cảnh báo, không biết có cảnh báo nào. Không tool. */
    LLM_CODE,
    /** Đoạn mã + nội dung cảnh báo (công cụ, quy tắc, thông điệp). Không tool. */
    LLM_STATIC,
    /** Agent thật của hệ thống: tự truy xuất bằng chứng qua 5 tool. */
    EVIDENCE_AGENT;

    /** Giá trị trên dây gửi agent-service ({@code llm_code}…). */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
