package vn.hust.datn.evidence.service.linker;

import java.util.UUID;

/**
 * Một frame stack trace trỏ về mã production, đã đối chiếu được với snapshot.
 *
 * @param stackTraceEvidenceId evidence loại {@code STACK_TRACE} chứa frame này — chưa phải thứ ta
 *     muốn liên kết. Xem {@link TestCaseRow#stackTraceEvidenceId()}
 * @param rawLine nguyên văn dòng {@code at ...}, dùng làm {@code rationale} để người đọc báo cáo
 *     tra ngược được về artifact gốc
 */
public record StackFrameRow(
        UUID stackTraceEvidenceId, int ordinal, String filePath, Integer lineNumber, String rawLine) {}
