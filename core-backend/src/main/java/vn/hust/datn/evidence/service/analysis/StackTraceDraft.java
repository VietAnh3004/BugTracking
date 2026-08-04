package vn.hust.datn.evidence.service.analysis;

import java.util.List;
import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Stack trace của một test hỏng, tách thành evidence RIÊNG chứ không gộp vào {@link TestCaseDraft}.
 *
 * <p>Lý do: {@code evidence_record} chỉ có MỘT bộ {@code (file_path, start_line)}. Của
 * {@code FAILED_TEST} là file TEST ({@code CartServiceTest.java:37}), của {@code STACK_TRACE} là
 * frame production đầu tiên ({@code CartService.java:25}). Gộp lại thì mất một trong hai, và
 * EvidenceLinker ở tuần 10-11 mất đúng cái nó cần để nối warning ↔ test.
 *
 * <p>Hai record nối nhau bằng {@code traceCorrelationId} (khoá nội bộ của parser), rồi
 * {@code EvidenceMaterializer} đổi thành {@code test_case.stack_trace_evidence_id} — provenance
 * cấu trúc, KHÔNG phải {@code evidence_link} ngữ nghĩa (thứ đó để tuần 10-11).
 */
public record StackTraceDraft(
        EvidenceType type,
        EvidenceSource source,
        String rawFilePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        Map<String, Object> content,
        String traceCorrelationId,
        String exceptionType,
        String exceptionMessage,
        List<StackFrameDraft> frames)
        implements EvidenceDraft {}
