package vn.hust.datn.evidence.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Thực nghiệm 3 cấu hình ({@code docs/thuc-nghiem.md}).
 *
 * @param repeats số lần lặp mỗi ô (cảnh báo × cấu hình) — LLM không cho cùng kết quả mỗi lần, nên
 *     một lần là điểm yếu dễ bị hỏi nhất khi bảo vệ (kế hoạch §6)
 * @param maxConsecutiveFailures dừng cả lượt chạy sau chừng này ô hỏng LIÊN TIẾP. Trên Gemini free
 *     đó gần như luôn là hết hạn mức trong ngày: chạy tiếp chỉ đốt thêm lượt thử lại vô ích
 */
@Validated
@ConfigurationProperties(prefix = "app.experiment")
public record ExperimentProperties(@Min(1) @Max(10) int repeats, @Min(1) int maxConsecutiveFailures) {}
