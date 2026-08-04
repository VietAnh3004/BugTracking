package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/** Nhận dạng parser theo NỘI DUNG artifact, trên đúng bốn file thật của vulnshop. */
class AnalyzerRegistryTest {

    private final AnalyzerRegistry registry = new AnalyzerRegistry(
            List.of(new SpotbugsXmlAdapter(), new SemgrepSarifAdapter()),
            List.of(new SurefireResultCollector(), new JacocoCoverageCollector()));

    private NamedArtifact artifact(String name, byte[] content) {
        return new NamedArtifact(name, content);
    }

    /**
     * Chốt chặn chính: bốn định dạng phải phân biệt được lẫn nhau. {@code detect} ném khi có nhiều
     * hơn một parser nhận, nên bốn assert này cũng đồng thời chứng minh bốn hàm nhận dạng KHÔNG
     * chồng lấn.
     */
    @Test
    void detect_bonArtifactThat_moiCaiDungMotParserNhan() {
        assertThat(registry.detect(artifact("spotbugs.xml", SampleArtifacts.spotbugsXml()))
                        .toolKey())
                .isEqualTo("spotbugs");
        assertThat(registry.detect(artifact("semgrep.sarif", SampleArtifacts.semgrepSarif()))
                        .toolKey())
                .isEqualTo("semgrep");
        assertThat(registry.detect(artifact("jacoco.xml", SampleArtifacts.jacocoXml()))
                        .toolKey())
                .isEqualTo("jacoco");
        assertThat(registry.detect(artifact(
                                "TEST-x.xml",
                                SampleArtifacts.bytesOf(
                                        SampleArtifacts.surefireReports().get(0))))
                        .toolKey())
                .isEqualTo("surefire");
    }

    /**
     * Tên file KHÔNG được ảnh hưởng tới việc chọn parser: người dùng đặt nhầm hai phần multipart
     * là chuyện thường, và hậu quả phải là parse đúng chứ không phải một lỗi ở chỗ chẳng liên quan.
     */
    @Test
    void detect_tenFileSaiHoanToan_vanChonDungParserTheoNoiDung() {
        DetectedAnalyzer analyzer = registry.detect(artifact("jacoco.xml", SampleArtifacts.spotbugsXml()));

        assertThat(analyzer.toolKey()).isEqualTo("spotbugs");
        assertThat(analyzer.adapter()).isNotNull();
        assertThat(analyzer.collector()).isNull();
    }

    @Test
    void detect_khongParserNaoNhan_nemKemGoiY() {
        assertThatThrownBy(() -> registry.detect(artifact("anh.png", "khong phai artifact".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("anh.png")
                .hasMessageContaining("TEST-*.xml");
    }

    @Test
    void parse_traVeDraftDungLoaiChoCaHaiHoParser() {
        DetectedAnalyzer tinh = registry.detect(artifact("s.xml", SampleArtifacts.spotbugsXml()));
        DetectedAnalyzer phu = registry.detect(artifact("j.xml", SampleArtifacts.jacocoXml()));

        assertThat(tinh.parse(SampleArtifacts.spotbugsXml())).hasOnlyElementsOfType(WarningDraft.class);
        assertThat(phu.parse(SampleArtifacts.jacocoXml())).hasOnlyElementsOfType(CoverageDraft.class);
        assertThat(tinh.toolVersion(SampleArtifacts.spotbugsXml())).isNotBlank();
        // JaCoCo không khai phiên bản — null ở đây là bình thường, không phải thiếu dữ liệu.
        assertThat(phu.toolVersion(SampleArtifacts.jacocoXml())).isNull();
    }

    @Test
    void loadOrder_theoThuTuChuanSpotbugsSemgrepSurefireJacoco() {
        List<DetectedAnalyzer> daoNguoc = new java.util.ArrayList<>(List.of(
                DetectedAnalyzer.of(new JacocoCoverageCollector()),
                DetectedAnalyzer.of(new SurefireResultCollector()),
                DetectedAnalyzer.of(new SemgrepSarifAdapter()),
                DetectedAnalyzer.of(new SpotbugsXmlAdapter())));

        daoNguoc.sort(registry.canonicalOrder());

        assertThat(daoNguoc).extracting(DetectedAnalyzer::toolKey).containsExactly("spotbugs", "semgrep", "surefire", "jacoco");
    }

    @Test
    void constructor_haiAdapterCungTool_nem() {
        assertThatThrownBy(() -> new AnalyzerRegistry(
                        List.of(new SpotbugsXmlAdapter(), new SpotbugsXmlAdapter()), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cung khai tool");
    }

    /**
     * Một analyzer chưa khai thứ tự nạp phải làm ứng dụng KHÔNG khởi động được. Nếu chỉ xếp nó
     * xuống cuối, {@code seq_in_run} — tức Evidence ID — sẽ phụ thuộc thứ tự Spring quét bean và
     * đổi âm thầm giữa hai lần deploy.
     */
    @Test
    void constructor_analyzerChuaKhaiThuTuNap_khongKhoiDongDuoc() {
        assertThatThrownBy(() -> new AnalyzerRegistry(List.of(), List.of(new CollectorKhongKhaiThuTu())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANONICAL_ORDER")
                .hasMessageContaining("parser");
    }

    @Test
    void detectedAnalyzer_khongTheVuaLaAdapterVuaLaCollector() {
        assertThatThrownBy(() -> new DetectedAnalyzer(new SpotbugsXmlAdapter(), new JacocoCoverageCollector()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DetectedAnalyzer(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adapterFor_toolKhongDangKy_nem() {
        AnalyzerRegistry chiCoSpotbugs = new AnalyzerRegistry(List.of(new SpotbugsXmlAdapter()), List.of());

        assertThat(chiCoSpotbugs.adapterFor(WarningTool.spotbugs)).isInstanceOf(SpotbugsXmlAdapter.class);
        assertThatThrownBy(() -> chiCoSpotbugs.adapterFor(WarningTool.semgrep)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> chiCoSpotbugs.collectorFor(EvidenceSource.jacoco)).isInstanceOf(IllegalStateException.class);
    }

    /** {@code EvidenceSource.parser} cố tình không nằm trong thứ tự nạp — nó không phải analyzer. */
    private static final class CollectorKhongKhaiThuTu implements TestEvidenceCollector {
        @Override
        public EvidenceSource supportedSource() {
            return EvidenceSource.parser;
        }

        @Override
        public boolean looksLikeSupportedArtifact(byte[] rawArtifact) {
            return false;
        }

        @Override
        public String toolVersion(byte[] rawArtifact) {
            return null;
        }

        @Override
        public List<EvidenceDraft> collect(byte[] rawArtifact) {
            return List.of();
        }
    }
}
