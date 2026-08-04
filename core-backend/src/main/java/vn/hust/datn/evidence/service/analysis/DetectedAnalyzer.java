package vn.hust.datn.evidence.service.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser đã được {@link AnalyzerRegistry#detect} chọn cho một artifact — đúng MỘT trong hai trường
 * khác null.
 *
 * <p>Tồn tại để tầng điều phối không phải viết hai nhánh gần như giống hệt nhau cho hai họ parser:
 * {@link StaticAnalysisAdapter} và {@link TestEvidenceCollector} có cùng ba thao tác (nhận dạng,
 * đọc phiên bản, parse) nhưng khác kiểu trả về, và gộp hai interface làm một sẽ ép
 * {@code StaticAnalysisAdapter} mất kiểu trả về hẹp {@code List<WarningDraft>} mà các test của nó
 * đang dựa vào.
 */
public record DetectedAnalyzer(StaticAnalysisAdapter adapter, TestEvidenceCollector collector) {

    public DetectedAnalyzer {
        if ((adapter == null) == (collector == null)) {
            throw new IllegalArgumentException("DetectedAnalyzer phai co dung mot trong hai: adapter hoac collector");
        }
    }

    public static DetectedAnalyzer of(StaticAnalysisAdapter adapter) {
        return new DetectedAnalyzer(adapter, null);
    }

    public static DetectedAnalyzer of(TestEvidenceCollector collector) {
        return new DetectedAnalyzer(null, collector);
    }

    /** {@code spotbugs} | {@code semgrep} | {@code surefire} | {@code jacoco} — khớp khoá của {@code tool_versions}. */
    public String toolKey() {
        return adapter != null ? adapter.supportedTool().name() : collector.supportedSource().name();
    }

    public List<EvidenceDraft> parse(byte[] rawArtifact) {
        if (collector != null) {
            return collector.collect(rawArtifact);
        }
        // List<WarningDraft> không phải List<EvidenceDraft> (generic bất biến) nên phải sao chép
        // sang danh sách kiểu rộng hơn — không phải chuyển kiểu thừa.
        return new ArrayList<>(adapter.parse(rawArtifact));
    }

    /** null khi công cụ không khai phiên bản trong artifact (JaCoCo) — bình thường, không phải thiếu dữ liệu. */
    public String toolVersion(byte[] rawArtifact) {
        return adapter != null ? adapter.toolVersion(rawArtifact) : collector.toolVersion(rawArtifact);
    }
}
