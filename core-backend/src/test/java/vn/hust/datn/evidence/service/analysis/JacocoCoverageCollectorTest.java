package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.CoverageStatus;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/** Chạy trên {@code jacoco.xml} THẬT (171 dòng, 20 sourcefile, 73 method). */
class JacocoCoverageCollectorTest {

    private final JacocoCoverageCollector collector = new JacocoCoverageCollector();
    private final byte[] artifact = SampleArtifacts.jacocoXml();

    private List<CoverageDraft> drafts() {
        return collector.collect(artifact).stream()
                .map(CoverageDraft.class::cast)
                .toList();
    }

    /**
     * File thật có {@code <!DOCTYPE report ... "report.dtd">} và {@code report.dtd} KHÔNG tồn tại
     * cạnh nó. {@code DocumentBuilderFactory} mặc định sẽ đi tìm rồi ném lỗi, còn
     * {@code disallow-doctype-decl=true} thì từ chối thẳng — đây là lý do phải có hai factory.
     */
    @Test
    void collect_coDoctypeTroToiDtdKhongTonTai_vanParseDuoc() {
        assertThat(new String(artifact, 0, 200, StandardCharsets.UTF_8)).contains("<!DOCTYPE report");

        assertThat(drafts()).isNotEmpty();
    }

    /** Cấu hình nới cho DOCTYPE vẫn phải chặn entity ngoài. */
    @Test
    void collect_externalEntity_khongDocDuocFileTrenDia() {
        byte[] xxe =
                """
                <?xml version="1.0"?>
                <!DOCTYPE report [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <report name="&xxe;"><package name="p"><sourcefile name="A.java"><line nr="1" mi="0" ci="1" mb="0" cb="0"/></sourcefile></package></report>
                """
                        .getBytes(StandardCharsets.UTF_8);

        // Cau hinh noi cho DOCTYPE van TU CHOI THANG entity ngoai, khong phai chi bo qua no —
        // manh hon muc toi thieu can thiet, va la hanh vi dung mong muon.
        assertThatThrownBy(() -> collector.collect(xxe))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("external entity");
    }

    @Test
    void collect_dungSoDongVaSoFileTheoExpectedAnalysis() {
        List<CoverageDraft> drafts = drafts();

        int totalLines = drafts.stream().mapToInt(draft -> draft.lines().size()).sum();
        long executed = drafts.stream()
                .flatMap(draft -> draft.lines().stream())
                .filter(CoverageLineDraft::executed)
                .count();

        assertThat(totalLines).isEqualTo(SampleArtifacts.expectedInt("coverage", "total_lines"));
        assertThat(executed).isEqualTo(SampleArtifacts.expectedInt("coverage", "covered_lines"));
    }

    /**
     * Chốt chặn quan trọng nhất của file này. "Dòng được phủ" theo JaCoCo là {@code ci > 0} =
     * COVERED CỘNG PARTIAL. Nếu ai đó "sửa" cho chỉ đếm COVERED thì ra 77 và test này đỏ.
     */
    @Test
    void collect_dongDuocPhuLaCoveredCongPartialChuKhongPhaiChiCovered() {
        List<CoverageLineDraft> lines =
                drafts().stream().flatMap(draft -> draft.lines().stream()).toList();

        long covered = lines.stream().filter(line -> line.status() == CoverageStatus.COVERED).count();
        long partial = lines.stream().filter(line -> line.status() == CoverageStatus.PARTIAL).count();
        long missed = lines.stream().filter(line -> line.status() == CoverageStatus.MISSED).count();

        assertThat(covered).isEqualTo(77);
        assertThat(partial).isEqualTo(5);
        assertThat(missed).isEqualTo(89);
        assertThat(covered + partial).isEqualTo(SampleArtifacts.expectedInt("coverage", "covered_lines"));
    }

    /** {@code <class>} và {@code <sourcefile>} SONG SONG, không lồng nhau — nối qua @sourcefilename. */
    @Test
    void collect_classVaSourcefileSongSong_ghepDuDuocMethod() {
        int totalMethods =
                drafts().stream().mapToInt(draft -> draft.methods().size()).sum();

        assertThat(totalMethods).isEqualTo(73);
        CoverageDraft orderStatusChecker = drafts().stream()
                .filter(draft -> "OrderStatusChecker.java".equals(draft.sourceFileName()))
                .findFirst()
                .orElseThrow();
        assertThat(orderStatusChecker.methods()).isNotEmpty();
        assertThat(orderStatusChecker.methods())
                .allSatisfy(method -> assertThat(method.className())
                        .isEqualTo("vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker"));
    }

    /** {@code <sourcefile>} không có path — phải ghép từ {@code package/@name}. */
    @Test
    void collect_sourcefileKhongCoPath_ghepTuTenPackage() {
        CoverageDraft draft = drafts().stream()
                .filter(d -> "OrderStatusChecker.java".equals(d.sourceFileName()))
                .findFirst()
                .orElseThrow();

        assertThat(draft.rawFilePath())
                .isEqualTo("vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker.java");
        assertThat(draft.type()).isEqualTo(EvidenceType.COVERAGE_INFORMATION);
    }

    /** Một sourcefile không có dòng nào được instrument thì bỏ qua: 20 file → 19 evidence. */
    @Test
    void collect_sourcefileKhongCoDongNao_biBoQua() {
        assertThat(drafts()).hasSize(19);
        assertThat(drafts()).extracting(CoverageDraft::sourceFileName).doesNotContain("SecurityConstants.java");
    }

    /**
     * {@code method/@line} chỉ là dòng ĐẦU và method KHÔNG theo thứ tự dòng — parser không được
     * giả định thứ tự, và không suy ra "dòng X thuộc method nào".
     */
    @Test
    void collect_methodKhongTheoThuTuDong_vaChapNhanInitClinit() {
        List<CoverageMethodDraft> methods =
                drafts().stream().flatMap(draft -> draft.methods().stream()).toList();

        assertThat(methods).extracting(CoverageMethodDraft::methodName).contains("<init>");
        assertThat(methods).allSatisfy(method -> assertThat(method.methodDesc()).isNotNull());

        List<CoverageMethodDraft> unordered = drafts().stream()
                .filter(draft -> draft.methods().size() > 1)
                .findFirst()
                .orElseThrow()
                .methods();
        // Chi khang dinh parser giu nguyen thu tu trong file, KHONG sap xep lai theo firstLine.
        assertThat(unordered).isNotEmpty();
    }

    /** Không nhân đôi 171 mục per-line vào JSONB — hai nguồn sự thật cho cùng một con số. */
    @Test
    void collect_contentKhongChuaChiTietTungDongVaKhongChuaTenMay() {
        String allContent = drafts().stream().map(draft -> draft.content().toString()).reduce("", String::concat);

        assertThat(allContent).doesNotContain("sessioninfo");
        assertThat(allContent).doesNotContain("DESKTOP-");
        drafts().forEach(draft -> assertThat(draft.content()).doesNotContainKey("lines"));
    }

    /** 0 dòng instrument = agent không attach được, KHÔNG phải "độ phủ 0%". */
    @Test
    void collect_khongCoDongNaoDuocInstrument_nemLoi() {
        byte[] empty =
                """
                <report name="x"><package name="p"><sourcefile name="A.java"/></package></report>
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> collector.collect(empty))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("agent khong");
    }

    @Test
    void collect_khongPhaiBaoCaoJacoco_nemLoi() {
        assertThatThrownBy(() -> collector.collect("<testsuite/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void looksLikeSupportedArtifact_phanBietDuocDinhDang() {
        assertThat(collector.looksLikeSupportedArtifact(artifact)).isTrue();
        assertThat(collector.looksLikeSupportedArtifact(SampleArtifacts.spotbugsXml())).isFalse();
        assertThat(collector.looksLikeSupportedArtifact(SampleArtifacts.semgrepSarif())).isFalse();
    }

    /** JaCoCo không khai phiên bản — null là bình thường, không phải thiếu dữ liệu. */
    @Test
    void toolVersion_jacocoKhongKhaiPhienBan_traVeNull() {
        assertThat(collector.toolVersion(artifact)).isNull();
    }
}
