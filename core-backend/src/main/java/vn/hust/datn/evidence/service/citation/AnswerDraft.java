package vn.hust.datn.evidence.service.citation;

import java.math.BigDecimal;
import java.util.List;

import vn.hust.datn.evidence.domain.ClaimVerdict;

/**
 * Câu trả lời NGUYÊN BẢN của LLM, trước hậu kiểm.
 *
 * <p>Kiểu riêng chứ không dùng thẳng DTO của agent-service: {@link CitationValidator} phải chạy
 * được mà không cần biết câu trả lời đến từ HTTP, từ test, hay từ trình chạy thực nghiệm tuần 15.
 * Đó cũng chính là điều làm cho "hậu kiểm độc lập với LLM" là sự thật kiểm được chứ không phải
 * một câu trong báo cáo.
 *
 * @param verdict kết luận LLM tự đưa ra
 * @param summary câu trả lời ngắn cho người dùng
 * @param claims các nhận định, theo đúng thứ tự LLM sinh ra
 * @param contradictingShortIds bằng chứng phản bác — KHÔNG đi qua {@code claim_citation}, xem
 *     {@link vn.hust.datn.evidence.domain.Message#getContradictingShortIds()}
 */
public record AnswerDraft(
        ClaimVerdict verdict, String summary, List<ClaimDraft> claims, List<String> contradictingShortIds) {

    /** Một nhận định đơn lẻ kèm danh sách Evidence ID nguyên văn LLM viết ra. */
    public record ClaimDraft(String text, List<String> citedShortIds, BigDecimal confidence) {}
}
