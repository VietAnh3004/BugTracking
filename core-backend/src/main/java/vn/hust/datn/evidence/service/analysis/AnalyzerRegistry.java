package vn.hust.datn.evidence.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Chọn parser cho một artifact, và định nghĩa THỨ TỰ NẠP chuẩn.
 *
 * <h2>Nhận dạng theo NỘI DUNG, không theo tên file</h2>
 *
 * Endpoint nạp artifact nhận bốn phần multipart có tên. Nếu tin vào tên đó, người dùng đặt nhầm
 * hai phần sẽ khiến {@code spotbugs.xml} được đưa cho parser Semgrep và lỗi nổ ra ở một chỗ chẳng
 * liên quan gì tới nguyên nhân. Mỗi parser đã có sẵn {@code looksLikeSupportedArtifact} — cùng vai
 * trò {@code validateFormat()} của {@code SnapshotSourceIngestor} — nên chỉ việc hỏi tất cả và đòi
 * ĐÚNG MỘT cái nhận.
 *
 * <p>Không cái nào nhận, hoặc nhiều hơn một cái nhận, đều là lỗi ồn ào. Trường hợp sau nghĩa là hai
 * hàm nhận dạng chồng lấn — một khiếm khuyết cần lộ ra ngay chứ không phải im lặng chọn cái đầu.
 *
 * <h2>Vì sao có registry ở đây trong khi {@code SnapshotSourceIngestor} thì không</h2>
 *
 * Javadoc của ingestor ghi rõ "chỉ một implementation thì không cần cơ chế chọn lựa". Ở đây có
 * BỐN implementation thật ngay từ đầu (SpotBugs, Semgrep, Surefire, JaCoCo). Đây là thay đổi có
 * lý do, không phải quên quy ước.
 */
@Component
public class AnalyzerRegistry {

    /**
     * Thứ tự nạp CỐ ĐỊNH. Đây không phải chi tiết thẩm mỹ: thứ tự draft đi vào
     * {@link EvidenceMaterializer} trở thành {@code seq_in_run}, tức trở thành Evidence ID mà LLM
     * trích dẫn. Đổi thứ tự là đổi ID của mọi bằng chứng trong mọi run về sau.
     */
    private static final List<String> CANONICAL_ORDER = List.of("spotbugs", "semgrep", "surefire", "jacoco");

    private final Map<WarningTool, StaticAnalysisAdapter> adapters = new EnumMap<>(WarningTool.class);
    private final Map<EvidenceSource, TestEvidenceCollector> collectors = new EnumMap<>(EvidenceSource.class);

    public AnalyzerRegistry(List<StaticAnalysisAdapter> staticAdapters, List<TestEvidenceCollector> testCollectors) {
        for (StaticAnalysisAdapter adapter : staticAdapters) {
            StaticAnalysisAdapter existing = adapters.put(adapter.supportedTool(), adapter);
            if (existing != null) {
                throw new IllegalStateException("Hai StaticAnalysisAdapter cung khai tool '%s': %s va %s"
                        .formatted(
                                adapter.supportedTool(),
                                existing.getClass().getName(),
                                adapter.getClass().getName()));
            }
        }
        for (TestEvidenceCollector collector : testCollectors) {
            TestEvidenceCollector existing = collectors.put(collector.supportedSource(), collector);
            if (existing != null) {
                throw new IllegalStateException("Hai TestEvidenceCollector cung khai source '%s': %s va %s"
                        .formatted(
                                collector.supportedSource(),
                                existing.getClass().getName(),
                                collector.getClass().getName()));
            }
        }

        // Kiểm LÚC KHỞI ĐỘNG, không phải lúc sắp xếp: một tool mới chưa khai thứ tự sẽ khiến
        // seq_in_run — tức Evidence ID — phụ thuộc thứ tự Spring quét bean, thay đổi âm thầm giữa
        // hai lần deploy. Ứng dụng không lên được còn hơn Evidence Store trôi ID.
        List<String> chuaKhaiThuTu = new ArrayList<>();
        adapters.keySet().forEach(tool -> addIfUnordered(chuaKhaiThuTu, tool.name()));
        collectors.keySet().forEach(source -> addIfUnordered(chuaKhaiThuTu, source.name()));
        if (!chuaKhaiThuTu.isEmpty()) {
            throw new IllegalStateException(
                    "Cac analyzer sau chua khai trong AnalyzerRegistry.CANONICAL_ORDER: %s. Thieu thu tu thi seq_in_run "
                                    .formatted(chuaKhaiThuTu)
                            + "phu thuoc thu tu quet bean va Evidence ID doi giua hai lan deploy.");
        }
    }

    private static void addIfUnordered(List<String> missing, String toolKey) {
        if (!CANONICAL_ORDER.contains(toolKey)) {
            missing.add(toolKey);
        }
    }

    public StaticAnalysisAdapter adapterFor(WarningTool tool) {
        StaticAnalysisAdapter adapter = adapters.get(tool);
        if (adapter == null) {
            throw new IllegalStateException("Khong co StaticAnalysisAdapter cho tool " + tool);
        }
        return adapter;
    }

    public TestEvidenceCollector collectorFor(EvidenceSource source) {
        TestEvidenceCollector collector = collectors.get(source);
        if (collector == null) {
            throw new IllegalStateException("Khong co TestEvidenceCollector cho source " + source);
        }
        return collector;
    }

    /**
     * @throws InvalidArtifactException khi không parser nào nhận, hoặc nhiều hơn một parser nhận
     */
    public DetectedAnalyzer detect(NamedArtifact artifact) {
        List<DetectedAnalyzer> matches = new ArrayList<>();
        for (StaticAnalysisAdapter adapter : adapters.values()) {
            if (adapter.looksLikeSupportedArtifact(artifact.content())) {
                matches.add(DetectedAnalyzer.of(adapter));
            }
        }
        for (TestEvidenceCollector collector : collectors.values()) {
            if (collector.looksLikeSupportedArtifact(artifact.content())) {
                matches.add(DetectedAnalyzer.of(collector));
            }
        }

        if (matches.isEmpty()) {
            throw new InvalidArtifactException(
                    "Khong nhan dang duoc artifact '%s' (%d byte). Cho phep: spotbugs.xml, semgrep SARIF, "
                                    .formatted(artifact.name(), artifact.content().length)
                            + "bao cao Surefire TEST-*.xml, jacoco.xml.");
        }
        if (matches.size() > 1) {
            throw new InvalidArtifactException("Artifact '%s' duoc %d parser cung nhan (%s) — ham nhan dang bi chong lan"
                    .formatted(
                            artifact.name(),
                            matches.size(),
                            matches.stream().map(DetectedAnalyzer::toolKey).toList()));
        }
        return matches.get(0);
    }

    /**
     * Thứ tự nạp của một parser. Constructor đã bảo đảm mọi parser đăng ký đều có mặt trong
     * {@link #CANONICAL_ORDER}, nên ở đây không có nhánh "không tìm thấy".
     */
    public int loadOrderOf(DetectedAnalyzer analyzer) {
        return CANONICAL_ORDER.indexOf(analyzer.toolKey());
    }

    /**
     * So sánh ỔN ĐỊNH theo thứ tự nạp chuẩn. Dùng {@code List.sort} (merge sort ổn định) nên hai
     * artifact cùng tool giữ nguyên thứ tự người gọi đưa vào — điều kiện để nhiều file
     * {@code TEST-*.xml} cho ra cùng một dãy {@code seq_in_run} ở mọi lần chạy.
     */
    public Comparator<DetectedAnalyzer> canonicalOrder() {
        return Comparator.comparingInt(this::loadOrderOf);
    }
}
