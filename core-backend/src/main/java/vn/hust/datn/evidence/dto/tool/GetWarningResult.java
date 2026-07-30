package vn.hust.datn.evidence.dto.tool;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.WarningView;

/**
 * FLATTEN, không lồng {@code { evidence: {...}, warning: {...} } } — suy trực tiếp từ ADR 0004
 * §(2): "trích dẫn một warning" và "trích dẫn bất kỳ evidence" phải là CÙNG một cơ chế. Lồng hai
 * object hai id khiến LLM citation mơ hồ, tự tạo nguồn lỗi Evidence Citation Accuracy giả tạo.
 *
 * <p>{@code source}/{@code tool} chữ thường (spotbugs/semgrep) là CỐ Ý — phản chiếu đúng CHECK
 * constraint trong DB, không thêm tầng mapping mà agent-service phải sao chép. Đừng "chuẩn hoá".
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GetWarningResult(
        String shortId,
        UUID analysisRunId,
        String filePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        WarningTool tool,
        String ruleId,
        String ruleIdNormalized,
        String severityRaw,
        WarningSeverity severityNormalized,
        boolean hasPrimaryLocation) {

    public static GetWarningResult from(WarningView view) {
        return new GetWarningResult(
                view.shortId(),
                view.analysisRunId(),
                view.filePath(),
                view.startLine(),
                view.endLine(),
                view.symbol(),
                view.summary(),
                view.tool(),
                view.ruleId(),
                view.ruleIdNormalized(),
                view.severityRaw(),
                view.severityNormalized(),
                view.hasPrimaryLocation());
    }
}
