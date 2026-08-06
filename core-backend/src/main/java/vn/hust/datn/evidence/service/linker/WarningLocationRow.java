package vn.hust.datn.evidence.service.linker;

import java.util.UUID;

/**
 * Một vị trí cảnh báo đã đối chiếu được với snapshot, dạng phẳng cho EvidenceLinker.
 *
 * <p>Không dùng entity {@code WarningLocation} vì nó khoá sang {@code evidence_record} bằng UUID
 * trần: trả entity thì phải nạp lượt hai chỉ để lấy {@code short_id}.
 */
public record WarningLocationRow(
        UUID evidenceRecordId,
        String shortId,
        int ordinal,
        String filePath,
        Integer startLine,
        Integer endLine,
        String className,
        String methodName) {}
