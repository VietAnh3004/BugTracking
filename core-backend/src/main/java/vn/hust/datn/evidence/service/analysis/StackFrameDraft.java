package vn.hust.datn.evidence.service.analysis;

import vn.hust.datn.evidence.domain.StackFrameKind;

/**
 * Một dòng {@code at ...} đã diễn giải.
 *
 * @param rawLine        nguyên văn — nguồn duy nhất để reparse khi heuristic sai
 * @param modulePrefix   {@code java.base} (module JPMS) hoặc {@code app} (classloader); null nếu không có
 * @param sourceFileName null cho {@code (Native Method)}/{@code (Unknown Source)} — KHÔNG CÓ tên file,
 *                       khác hẳn "có tên file nhưng không đối chiếu được với snapshot"
 * @param lineNumber     null khi dòng không mang số
 */
public record StackFrameDraft(
        int ordinal,
        String rawLine,
        String modulePrefix,
        String declaringClass,
        String methodName,
        String sourceFileName,
        Integer lineNumber,
        StackFrameKind frameKind) {

    /** Đường dẫn suy ra để đối chiếu với snapshot: package của class + tên file. */
    public String rawFilePath() {
        if (sourceFileName == null || declaringClass == null) {
            return null;
        }
        int lastDot = declaringClass.lastIndexOf('.');
        if (lastDot < 0) {
            return sourceFileName;
        }
        return declaringClass.substring(0, lastDot).replace('.', '/') + "/" + sourceFileName;
    }
}
