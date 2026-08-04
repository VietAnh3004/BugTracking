package vn.hust.datn.evidence.service.analysis;

import java.util.List;
import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Cảnh báo tĩnh đã parse (SpotBugs hoặc Semgrep), chưa ghi DB.
 *
 * @param hasPrimaryLocation tool có tự đánh dấu vị trí chính hay không. false với 5/28 BugInstance
 *                           thật (chỉ có {@code synthetic='true'}) — khi đó parser chọn vị trí đầu
 *                           tiên, và cờ này ghi lại rằng đó là lựa chọn của TA chứ không phải của
 *                           tool
 * @param sourceIndex        thứ tự phần tử trong file gốc. Là khoá tự nhiên DUY NHẤT dùng được:
 *                           {@code fingerprints["matchBasedId/v1"]} của Semgrep là chuỗi literal
 *                           {@code "requires login"} giống hệt nhau ở cả 5 result
 * @param locations           luôn có ít nhất một phần tử
 */
public record WarningDraft(
        EvidenceType type,
        EvidenceSource source,
        String rawFilePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        Map<String, Object> content,
        WarningTool tool,
        String ruleId,
        String ruleIdNormalized,
        String severityRaw,
        WarningSeverity severityNormalized,
        List<Integer> cweIds,
        boolean hasPrimaryLocation,
        int sourceIndex,
        List<WarningLocationDraft> locations)
        implements EvidenceDraft {}
