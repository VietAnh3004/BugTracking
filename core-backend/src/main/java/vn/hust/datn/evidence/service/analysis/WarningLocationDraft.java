package vn.hust.datn.evidence.service.analysis;

/**
 * Một vị trí của cảnh báo, chưa chuẩn hoá đường dẫn.
 *
 * @param ordinal    thứ tự trong artifact gốc, bắt đầu từ 0
 * @param roleRaw    {@code SourceLine/@role} nguyên văn; null với 27/33 vị trí thật
 * @param primary    tool tự đánh dấu {@code primary='true'}, không phải parser đoán
 * @param pathRaw    đường dẫn nguyên văn
 * @param startLine  null khi tool không cho số dòng
 */
public record WarningLocationDraft(
        int ordinal,
        String roleRaw,
        boolean primary,
        String pathRaw,
        Integer startLine,
        Integer endLine,
        String className,
        String methodName) {}
