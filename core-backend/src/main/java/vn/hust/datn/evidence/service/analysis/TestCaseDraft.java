package vn.hust.datn.evidence.service.analysis;

import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.domain.TestFailureKind;

/**
 * Một test case đã parse.
 *
 * @param traceCorrelationId khoá NỘI BỘ nối sang {@link StackTraceDraft} của cùng test. Parser
 *     không được sinh UUID — UUID chỉ ra đời ở {@code EvidenceMaterializer}; nếu parser tự sinh
 *     thì nó trở thành thứ có trạng thái và không unit-test thuần được nữa. Null khi test không hỏng.
 */
public record TestCaseDraft(
        EvidenceType type,
        EvidenceSource source,
        String rawFilePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        Map<String, Object> content,
        String className,
        String methodName,
        String displayName,
        TestCaseStatus status,
        TestFailureKind failureKind,
        String failureType,
        String failureMessage,
        String stackTraceRaw,
        Integer durationMs,
        String reportFile,
        String traceCorrelationId)
        implements EvidenceDraft {}
