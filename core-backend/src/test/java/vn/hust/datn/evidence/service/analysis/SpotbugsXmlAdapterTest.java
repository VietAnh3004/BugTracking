package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/**
 * Chạy trên artifact THẬT ({@code docs/samples/vulnshop/spotbugs.xml}), không phải fixture bịa.
 * Con số kỳ vọng đọc từ {@code expected-analysis.json} — nguồn sự thật duy nhất.
 */
class SpotbugsXmlAdapterTest {

    private final SpotbugsXmlAdapter adapter = new SpotbugsXmlAdapter();
    private final byte[] artifact = SampleArtifacts.spotbugsXml();

    @Test
    void parse_artifactThat_dungSoCanhBaoTheoExpectedAnalysis() {
        List<WarningDraft> drafts = adapter.parse(artifact);

        assertThat(drafts).hasSize(SampleArtifacts.expectedInt("spotbugs", "findings"));
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.tool()).isEqualTo(WarningTool.spotbugs);
            assertThat(draft.locations()).isNotEmpty();
            assertThat(draft.summary()).isNotBlank();
        });
    }

    /**
     * Bẫy số một của cả đợt parser: {@code getElementsByTagName} trong Java là ĐỆ QUY, mà
     * {@code <SourceLine>} xuất hiện ở ba tầng lồng nhau (trong Class, trong Method, và con trực
     * tiếp). Dùng nhầm sẽ ra 90 vị trí thay vì 33 — và không có gì báo lỗi.
     */
    @Test
    void parse_sourceLineLongBaTang_chiLayConTrucTiep() {
        int totalLocations = adapter.parse(artifact).stream()
                .mapToInt(draft -> draft.locations().size())
                .sum();

        assertThat(totalLocations).isEqualTo(33);
    }

    /** 25/28 có đúng 1 vị trí, 3/28 có nhiều hơn — không nhét được vào một hàng 1:1. */
    @Test
    void parse_baBugCoNhieuViTri_sinhDuWarningLocation() {
        List<WarningDraft> multi = adapter.parse(artifact).stream()
                .filter(draft -> draft.locations().size() > 1)
                .toList();

        assertThat(multi).hasSize(3);

        WarningDraft hashEquals = multi.stream()
                .filter(draft -> "UNSAFE_HASH_EQUALS".equals(draft.ruleId()))
                .findFirst()
                .orElseThrow();
        assertThat(hashEquals.locations()).hasSize(3);
        assertThat(hashEquals.locations()).extracting(WarningLocationDraft::ordinal).containsExactly(0, 1, 2);
        // Ba vị trí trỏ tới BA FILE KHÁC NHAU — đây là lý do warning_location phải là bảng riêng.
        assertThat(hashEquals.locations())
                .extracting(WarningLocationDraft::pathRaw)
                .doesNotHaveDuplicates()
                .hasSize(3);
    }

    /** 5/28 chỉ có {@code synthetic='true'}: phải fallback, và phải ghi lại rằng đã fallback. */
    @Test
    void parse_khongCoViTriPrimary_fallbackViTriDauVaHaCo() {
        List<WarningDraft> withoutPrimary = adapter.parse(artifact).stream()
                .filter(draft -> !draft.hasPrimaryLocation())
                .toList();

        assertThat(withoutPrimary).hasSize(5);
        assertThat(withoutPrimary)
                .extracting(WarningDraft::ruleId)
                .containsOnly("SPRING_ENDPOINT", "HE_EQUALS_USE_HASHCODE");
        // Vẫn phải có vị trí để dùng, không được rơi về NULL.
        assertThat(withoutPrimary).allSatisfy(draft -> assertThat(draft.rawFilePath()).isNotBlank());
    }

    /** priority=1 là CAO NHẤT — trái trực giác, nên khẳng định tường minh cả ba nhánh. */
    @Test
    void parse_priorityMotLaCaoNhat_mapDungBaMuc() {
        List<WarningDraft> drafts = adapter.parse(artifact);

        assertThat(severityOf(drafts, "1")).containsOnly(WarningSeverity.HIGH);
        assertThat(severityOf(drafts, "2")).containsOnly(WarningSeverity.MEDIUM);
        assertThat(severityOf(drafts, "3")).containsOnly(WarningSeverity.LOW);

        // Phân phối thật của vulnshop, khớp <FindBugsSummary priority_1='5' priority_2='16' priority_3='7'>.
        assertThat(severityOf(drafts, "1")).hasSize(5);
        assertThat(severityOf(drafts, "2")).hasSize(16);
        assertThat(severityOf(drafts, "3")).hasSize(7);
    }

    /** 8/28 thiếu {@code @cweid} — phải là null, KHÔNG phải mảng rỗng (hai thứ khác nghĩa). */
    @Test
    void parse_thieuCweid_traVeNullChuKhongPhaiMangRong() {
        List<WarningDraft> drafts = adapter.parse(artifact);

        assertThat(drafts.stream().filter(draft -> draft.cweIds() == null).toList())
                .hasSize(8);
        assertThat(drafts.stream()
                        .filter(draft -> draft.cweIds() != null)
                        .allMatch(draft -> !draft.cweIds().isEmpty()))
                .isTrue();
    }

    /** 1/28 BugInstance không có thẻ {@code <Method>} — symbol chỉ còn class, không được NPE. */
    @Test
    void parse_bugKhongCoTheMethod_symbolChiConClass() {
        WarningDraft draft = adapter.parse(artifact).stream()
                .filter(d -> "MS_MUTABLE_COLLECTION_PKGPROTECT".equals(d.ruleId()))
                .findFirst()
                .orElseThrow();

        assertThat(draft.symbol()).isNotNull().doesNotContain("#");
    }

    /** {@code role} giữ NGUYÊN VĂN, không enum hoá: detector mới sinh role mới. */
    @Test
    void parse_roleGiuNguyenVan_khongEnumHoa() {
        List<String> roles = adapter.parse(artifact).stream()
                .flatMap(draft -> draft.locations().stream())
                .map(WarningLocationDraft::roleRaw)
                .filter(role -> role != null)
                .toList();

        assertThat(roles).contains("SOURCE_LINE_KNOWN_NULL", "SOURCE_LINE_OBLIGATION_CREATED");
        // 27/33 vị trí thật không có role.
        assertThat(roles).hasSize(6);
    }

    /**
     * 0 cảnh báo phải là LỖI, không phải danh sách rỗng. Đã gặp thật hai lần (SpotBugs trên JDK
     * 25; find-sec-bugs khai sai chỗ trong pom) — pipeline "chạy thành công" nhưng Evidence Store
     * rỗng nguy hiểm hơn nhiều so với báo lỗi thẳng.
     */
    @Test
    void parse_khongCoBugInstanceNao_nemLoiChuKhongTraDanhSachRong() {
        byte[] empty = "<BugCollection version='4.9.3'></BugCollection>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.parse(empty))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("0 canh bao");
    }

    @Test
    void parse_khongPhaiBaoCaoSpotbugs_nemLoi() {
        byte[] wrong = "<report><package/></report>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.parse(wrong)).isInstanceOf(InvalidArtifactException.class);
    }

    /** XXE: artifact là dữ liệu KHÔNG tin cậy (repo do người dùng upload sinh ra nó). */
    @Test
    void parse_externalEntity_khongDocDuocFileTrenDia() {
        byte[] xxe =
                """
                <?xml version="1.0"?>
                <!DOCTYPE BugCollection [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <BugCollection version="4.9.3"><BugInstance type="&xxe;"/></BugCollection>
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.parse(xxe)).isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void toolVersion_docTuChinhArtifact() {
        assertThat(adapter.toolVersion(artifact)).isEqualTo("4.9.3");
    }

    @Test
    void looksLikeSupportedArtifact_phanBietDuocDinhDang() {
        assertThat(adapter.looksLikeSupportedArtifact(artifact)).isTrue();
        assertThat(adapter.looksLikeSupportedArtifact(SampleArtifacts.jacocoXml())).isFalse();
        assertThat(adapter.looksLikeSupportedArtifact(new byte[0])).isFalse();
        assertThat(adapter.looksLikeSupportedArtifact(null)).isFalse();
    }

    private static List<WarningSeverity> severityOf(List<WarningDraft> drafts, String priority) {
        return drafts.stream()
                .filter(draft -> priority.equals(draft.severityRaw()))
                .map(WarningDraft::severityNormalized)
                .toList();
    }
}
