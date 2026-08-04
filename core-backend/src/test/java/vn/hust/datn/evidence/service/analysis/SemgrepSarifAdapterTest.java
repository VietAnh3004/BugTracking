package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/** Chạy trên SARIF THẬT ({@code docs/samples/vulnshop/semgrep.sarif}, 5 result / 60 rule). */
class SemgrepSarifAdapterTest {

    private final SemgrepSarifAdapter adapter = new SemgrepSarifAdapter();
    private final byte[] artifact = SampleArtifacts.semgrepSarif();

    @Test
    void parse_artifactThat_dungSoCanhBaoTheoExpectedAnalysis() {
        List<WarningDraft> drafts = adapter.parse(artifact);

        assertThat(drafts).hasSize(SampleArtifacts.expectedInt("semgrep", "findings"));
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.tool()).isEqualTo(WarningTool.semgrep);
            // SARIF khong co logicalLocations nen KHONG BAO GIO co symbol.
            assertThat(draft.symbol()).isNull();
            assertThat(draft.locations()).hasSize(1);
        });
    }

    /**
     * {@code result} không có {@code level}: nếu parser đọc thẳng từ result thì mọi cảnh báo sẽ
     * tụt về INFO mà không có gì báo lỗi. Test này khẳng định giả định đó bằng chính artifact.
     */
    @Test
    void parse_resultKhongCoLevel_phaiJoinSangRules() throws Exception {
        JsonNode firstResult = new ObjectMapper().readTree(artifact).get("runs").get(0).get("results").get(0);
        assertThat(firstResult.has("level")).isFalse();
        // properties cua result la object RONG — precision khong nam o day.
        assertThat(firstResult.path("properties").isEmpty()).isTrue();

        List<WarningDraft> drafts = adapter.parse(artifact);
        assertThat(drafts).extracting(WarningDraft::severityNormalized).doesNotContain(WarningSeverity.INFO);
        assertThat(drafts).extracting(WarningDraft::severityRaw).doesNotContainNull();
    }

    @Test
    void parse_ruleIdNormalized_laSegmentCuoi() {
        List<WarningDraft> drafts = adapter.parse(artifact);

        assertThat(drafts)
                .extracting(WarningDraft::ruleIdNormalized)
                .containsExactlyInAnyOrder(
                        "unvalidated-redirect", "use-of-md5", "use-of-md5", "spring-sqli", "spring-sqli");
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.ruleId()).startsWith("work.semgrep-rules."));
    }

    /** CWE nhúng trong chuỗi tag {@code "CWE-328: Use of Weak Hash"}, không phải trường riêng. */
    @Test
    void parse_cweLayTuChuoiTag() {
        WarningDraft md5 = adapter.parse(artifact).stream()
                .filter(draft -> "use-of-md5".equals(draft.ruleIdNormalized()))
                .findFirst()
                .orElseThrow();

        assertThat(md5.cweIds()).containsExactly(328);
    }

    /**
     * Cả 5 result có {@code fingerprints["matchBasedId/v1"]} là chuỗi literal
     * {@code "requires login"} giống hệt nhau. Nếu ai đó dùng nó làm khoá tự nhiên thì 5 cảnh báo
     * sẽ gộp thành 1. Khoá phân biệt phải là {@code sourceIndex}.
     */
    @Test
    void parse_fingerprintTrungNhauHetVanRaNamEvidenceRieng() throws Exception {
        JsonNode results = new ObjectMapper().readTree(artifact).get("runs").get(0).get("results");
        List<String> fingerprints = new java.util.ArrayList<>();
        results.forEach(result -> fingerprints.add(result.path("fingerprints").path("matchBasedId/v1").asText()));
        assertThat(fingerprints).hasSize(5).containsOnly("requires login");

        List<WarningDraft> drafts = adapter.parse(artifact);
        assertThat(drafts).extracting(WarningDraft::sourceIndex).containsExactly(0, 1, 2, 3, 4);
        // Va content KHONG duoc chua fingerprints — luu vao la moi nguoi sau dung lam khoa.
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.content()).doesNotContainKey("fingerprints"));
    }

    /** {@code originalUriBaseIds} không tồn tại: giữ nguyên văn uri, chuẩn hoá là việc tầng trên. */
    @Test
    void parse_uriTuyetDoiTrongContainer_giuNguyenVan() throws Exception {
        JsonNode run = new ObjectMapper().readTree(artifact).get("runs").get(0);
        assertThat(run.has("originalUriBaseIds")).isFalse();

        assertThat(adapter.parse(artifact))
                .extracting(WarningDraft::rawFilePath)
                .allSatisfy(path -> assertThat(path).startsWith("/work/"));
    }

    @Test
    void parse_severityMapping_baNhanhCoMauThat() {
        // level=error -> HIGH, level=warning + precision=very-high -> MEDIUM.
        List<WarningDraft> drafts = adapter.parse(artifact);

        assertThat(drafts)
                .filteredOn(draft -> "error".equals(draft.severityRaw()))
                .allSatisfy(draft -> assertThat(draft.severityNormalized()).isEqualTo(WarningSeverity.HIGH));
        assertThat(drafts)
                .filteredOn(draft -> "warning".equals(draft.severityRaw()))
                .allSatisfy(draft -> assertThat(draft.severityNormalized()).isEqualTo(WarningSeverity.MEDIUM));
    }

    /**
     * Nhánh {@code warning + precision khác very-high → LOW} KHÔNG có mẫu thật (60/60 rule trong
     * SARIF thật đều {@code very-high}), nên phải bịa cả rule. Ghi rõ để người sau không mất công
     * đi tìm ví dụ thật.
     */
    @Test
    void parse_warningVoiPrecisionThap_xuongLow() {
        byte[] fabricated = sarifWith("warning", "\"precision\": \"medium\"");

        assertThat(adapter.parse(fabricated).get(0).severityNormalized()).isEqualTo(WarningSeverity.LOW);
    }

    @Test
    void parse_levelNote_xuongInfo() {
        assertThat(adapter.parse(sarifWith("note", "\"precision\": \"very-high\""))
                        .get(0)
                        .severityNormalized())
                .isEqualTo(WarningSeverity.INFO);
    }

    /** {@code runs} là MẢNG: join rules phải theo từng run, không gộp toàn cục. */
    @Test
    void parse_nhieuRun_joinRulesTheoTungRun() {
        byte[] twoRuns =
                """
                {"version":"2.1.0","runs":[
                  {"tool":{"driver":{"name":"Semgrep OSS","rules":[
                     {"id":"r.a","defaultConfiguration":{"level":"error"},"properties":{"precision":"very-high"}}]}},
                   "results":[{"ruleId":"r.a","message":{"text":"m"},
                     "locations":[{"physicalLocation":{"artifactLocation":{"uri":"/w/A.java"},"region":{"startLine":1}}}]}]},
                  {"tool":{"driver":{"name":"Semgrep OSS","rules":[
                     {"id":"r.a","defaultConfiguration":{"level":"note"},"properties":{"precision":"very-high"}}]}},
                   "results":[{"ruleId":"r.a","message":{"text":"m"},
                     "locations":[{"physicalLocation":{"artifactLocation":{"uri":"/w/B.java"},"region":{"startLine":2}}}]}]}
                ]}
                """
                        .getBytes(StandardCharsets.UTF_8);

        List<WarningDraft> drafts = adapter.parse(twoRuns);

        // Cung ruleId nhung hai run khai hai level khac nhau -> hai severity khac nhau.
        assertThat(drafts).extracting(WarningDraft::severityNormalized)
                .containsExactly(WarningSeverity.HIGH, WarningSeverity.INFO);
        assertThat(drafts).extracting(WarningDraft::sourceIndex).containsExactly(0, 1);
    }

    /** 0 rule = ruleset vendor không nạp được, mà SARIF vẫn hợp lệ với 0 finding — hỏng im lặng. */
    @Test
    void parse_khongNapDuocRuleNao_nemLoi() {
        byte[] noRules =
                """
                {"version":"2.1.0","runs":[{"tool":{"driver":{"name":"Semgrep OSS","rules":[]}},"results":[]}]}
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.parse(noRules))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("ruleset");
    }

    @Test
    void parse_ruleIdKhongCoTrongRules_vanRaEvidenceVoiInfo() {
        byte[] orphan =
                """
                {"version":"2.1.0","runs":[{"tool":{"driver":{"name":"Semgrep OSS","rules":[
                   {"id":"r.known","defaultConfiguration":{"level":"error"},"properties":{"precision":"very-high"}}]}},
                 "results":[{"ruleId":"r.unknown","message":{"text":"m"},
                   "locations":[{"physicalLocation":{"artifactLocation":{"uri":"/w/A.java"},"region":{"startLine":3}}}]}]}]}
                """
                        .getBytes(StandardCharsets.UTF_8);

        List<WarningDraft> drafts = adapter.parse(orphan);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).severityNormalized()).isEqualTo(WarningSeverity.INFO);
        assertThat(drafts.get(0).cweIds()).isNull();
    }

    @Test
    void parse_khongCoResultNao_nemLoi() {
        byte[] empty =
                """
                {"version":"2.1.0","runs":[{"tool":{"driver":{"name":"Semgrep OSS","rules":[
                   {"id":"r.a","defaultConfiguration":{"level":"error"}}]}},"results":[]}]}
                """
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.parse(empty))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("0 canh bao");
    }

    @Test
    void toolVersion_docTuChinhArtifact() {
        assertThat(adapter.toolVersion(artifact)).isEqualTo("1.170.1");
    }

    @Test
    void looksLikeSupportedArtifact_phanBietDuocDinhDang() {
        assertThat(adapter.looksLikeSupportedArtifact(artifact)).isTrue();
        assertThat(adapter.looksLikeSupportedArtifact(SampleArtifacts.spotbugsXml())).isFalse();
        assertThat(adapter.looksLikeSupportedArtifact(new byte[0])).isFalse();
    }

    private static byte[] sarifWith(String level, String precisionJson) {
        return """
                {"version":"2.1.0","runs":[{"tool":{"driver":{"name":"Semgrep OSS","rules":[
                   {"id":"r.a","defaultConfiguration":{"level":"%s"},"properties":{%s}}]}},
                 "results":[{"ruleId":"r.a","message":{"text":"m"},
                   "locations":[{"physicalLocation":{"artifactLocation":{"uri":"/w/A.java"},"region":{"startLine":1}}}]}]}]}
                """
                .formatted(level, precisionJson)
                .getBytes(StandardCharsets.UTF_8);
    }
}
