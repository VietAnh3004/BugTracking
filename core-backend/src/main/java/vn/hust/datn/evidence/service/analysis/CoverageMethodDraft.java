package vn.hust.datn.evidence.service.analysis;

/**
 * Độ phủ cấp method.
 *
 * @param className  dạng JaCoCo, phân tách bằng {@code /}
 * @param methodDesc descriptor JVM, phân biệt overload
 * @param firstLine  dòng ĐẦU; KHÔNG có dòng cuối vì JaCoCo không cung cấp — cũng là lý do không
 *                   suy được "dòng 42 thuộc method nào"
 */
public record CoverageMethodDraft(
        String className,
        String methodName,
        String methodDesc,
        Integer firstLine,
        int missedInstructions,
        int coveredInstructions,
        int missedBranches,
        int coveredBranches,
        int missedLines,
        int coveredLines) {}
