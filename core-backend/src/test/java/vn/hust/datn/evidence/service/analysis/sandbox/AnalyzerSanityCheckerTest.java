package vn.hust.datn.evidence.service.analysis.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.service.analysis.SpotbugsXmlAdapter;
import vn.hust.datn.evidence.service.analysis.WarningDraft;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/**
 * Chạy trên artifact THẬT do sandbox sinh ra ({@code docs/samples/vulnshop-sandbox/}), khác
 * {@code docs/samples/vulnshop/} vốn do {@code run-analysis.sh} chạy trên host.
 *
 * <p>Mỗi phép từ chối được kiểm bằng cách LÀM HỎNG đúng một thứ trong artifact thật rồi khẳng
 * định checker bắt được — thay vì bịa một XML tối giản, vốn chỉ chứng minh code chạy trên dữ liệu
 * do chính test dựng ra.
 */
class AnalyzerSanityCheckerTest {

    private final AnalyzerSanityChecker checker = new AnalyzerSanityChecker(new ObjectMapper());

    private Map<String, byte[]> boArtifactDayDu() {
        Map<String, byte[]> artifacts = new HashMap<>();
        artifacts.put(AnalyzerSanityChecker.SPOTBUGS_ARTIFACT, SampleArtifacts.sandboxSpotbugsXml());
        artifacts.put(AnalyzerSanityChecker.SEMGREP_ARTIFACT, SampleArtifacts.sandboxSemgrepSarif());
        artifacts.put(AnalyzerSanityChecker.JACOCO_ARTIFACT, SampleArtifacts.jacocoXml());
        artifacts.put(
                AnalyzerSanityChecker.SUREFIRE_PREFIX + "TEST-a.B.xml",
                SampleArtifacts.bytesOf(SampleArtifacts.surefireReports().get(0)));
        return artifacts;
    }

    /** Thay một chuỗi trong spotbugs.xml thật — mô phỏng đúng một thứ hỏng, giữ nguyên phần còn lại. */
    private Map<String, byte[]> spotbugsSuaDoi(String tim, String thay) {
        Map<String, byte[]> artifacts = boArtifactDayDu();
        String xml = new String(SampleArtifacts.sandboxSpotbugsXml(), StandardCharsets.UTF_8);
        assertThat(xml).as("chuoi can sua phai co that trong artifact").contains(tim);
        artifacts.put(AnalyzerSanityChecker.SPOTBUGS_ARTIFACT, xml.replace(tim, thay).getBytes(StandardCharsets.UTF_8));
        return artifacts;
    }

    @Test
    void check_artifactThatCuaSandbox_dat() {
        SanityVerdict verdict = checker.check(boArtifactDayDu());

        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.canaryOk()).isTrue();
        // 6 finding canary, tách khỏi 28 cảnh báo của người dùng (tổng 34 trong artifact).
        assertThat(verdict.canaryFindingCount()).isEqualTo(6);
        assertThat(verdict.reason()).isNull();
    }

    /** Thiếu file ⇒ FAILED, KHÔNG phải "0 cảnh báo". Ranh giới quan trọng nhất của tầng 1. */
    @Test
    void check_thieuArtifact_baoFAILEDChuKhongPhaiKhongCoCanhBao() {
        for (String bo : List.of(
                AnalyzerSanityChecker.SPOTBUGS_ARTIFACT,
                AnalyzerSanityChecker.SEMGREP_ARTIFACT,
                AnalyzerSanityChecker.JACOCO_ARTIFACT)) {
            Map<String, byte[]> artifacts = boArtifactDayDu();
            artifacts.remove(bo);

            SanityVerdict verdict = checker.check(artifacts);

            assertThat(verdict.ok()).as(bo).isFalse();
            assertThat(verdict.reason()).contains(bo).contains("khong phai '0 canh bao'");
        }
    }

    @Test
    void check_khongCoBaoCaoSurefire_tuChoi() {
        Map<String, byte[]> artifacts = boArtifactDayDu();
        artifacts.keySet().removeIf(name -> name.startsWith(AnalyzerSanityChecker.SUREFIRE_PREFIX));

        assertThat(checker.check(artifacts).reason()).contains("Surefire");
    }

    @Test
    void check_fileRong_bịTuChoiNhuThieuHan() {
        Map<String, byte[]> artifacts = boArtifactDayDu();
        artifacts.put(AnalyzerSanityChecker.JACOCO_ARTIFACT, new byte[0]);

        assertThat(checker.check(artifacts).ok()).isFalse();
    }

    /** ADR 0003 thành assertion trên DỮ LIỆU: JDK mới hơn cho 0 cảnh báo mà vẫn báo thành công. */
    @Test
    void check_chayTrenJdkKhac21_tuChoi() {
        SanityVerdict verdict = checker.check(spotbugsSuaDoi("java_version=\"21.0.11\"", "java_version=\"25.0.1\""));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.reason()).contains("25.0.1").contains("21.x");
    }

    @Test
    void check_quet0Class_tuChoi() {
        SanityVerdict verdict = checker.check(spotbugsSuaDoi("total_classes=\"21\"", "total_classes=\"0\""));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.reason()).contains("0 class");
    }

    @Test
    void check_spotbugsBaoLoi_tuChoi() {
        SanityVerdict verdict = checker.check(spotbugsSuaDoi("<Errors errors=\"0\"", "<Errors errors=\"3\""));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.reason()).contains("3 loi");
    }

    /**
     * Đã xảy ra THẬT một lần trong dự án (khai find-sec-bugs sai chỗ trong pom): SpotBugs vẫn
     * chạy, vẫn quét đủ class, chỉ mất sạch rule bảo mật.
     */
    @Test
    void check_findSecBugsKhongDuocNap_tuChoi() {
        SanityVerdict verdict = checker.check(spotbugsSuaDoi("com.h3xstream.findsecbugs", "com.vi.du.khac"));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.reason()).contains("find-sec-bugs khong duoc nap");
    }

    /** Ruleset không đọc được nhưng SARIF vẫn hoàn toàn hợp lệ — đúng kiểu hỏng im lặng cần chặn. */
    @Test
    void check_semgrepNap0Rule_tuChoi() {
        Map<String, byte[]> artifacts = boArtifactDayDu();
        artifacts.put(
                AnalyzerSanityChecker.SEMGREP_ARTIFACT,
                "{\"runs\":[{\"tool\":{\"driver\":{\"rules\":[]}},\"results\":[],\"invocations\":[{\"executionSuccessful\":true}]}]}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(checker.check(artifacts).reason()).contains("0 rule");
    }

    @Test
    void check_semgrepBaoChayThatBai_tuChoi() {
        Map<String, byte[]> artifacts = boArtifactDayDu();
        artifacts.put(
                AnalyzerSanityChecker.SEMGREP_ARTIFACT,
                "{\"runs\":[{\"tool\":{\"driver\":{\"rules\":[{\"id\":\"r\"}]}},\"results\":[],\"invocations\":[{\"executionSuccessful\":false}]}]}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(checker.check(artifacts).reason()).contains("executionSuccessful=false");
    }

    /**
     * Canary mất rule bảo mật: mọi tầng khác vẫn xanh (SpotBugs chạy, quét đủ class, plugin vẫn
     * khai trong XML) — chỉ canary bắt được.
     */
    @Test
    void check_canaryMatFindingBaoMat_tuChoiDuCacTangKhacDeuXanh() {
        SanityVerdict verdict = checker.check(spotbugsSuaDoi("WEAK_MESSAGE_DIGEST_MD5", "MOT_RULE_KHAC"));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.canaryOk()).isFalse();
        assertThat(verdict.reason()).contains("Canary khong ra du finding").contains("WEAK_MESSAGE_DIGEST_MD5");
        // Vẫn đếm được số canary còn lại — "ra mấy cái" hữu ích hơn nhiều so với true/false.
        assertThat(verdict.canaryFindingCount()).isPositive();
    }

    @Test
    void check_canaryMatFindingCuaSpotbugsCore_tuChoi() {
        SanityVerdict verdict = checker.check(spotbugsSuaDoi("DM_DEFAULT_ENCODING", "MOT_RULE_KHAC"));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.reason()).contains("DM_DEFAULT_ENCODING");
    }

    /**
     * Bằng chứng canary phải bị LOẠI trước khi materialize. Không chỉ vì {@code finding_count} sẽ
     * sai: đường dẫn {@code evidence/canary/Canary.java} không có trong snapshot người dùng, nên
     * mỗi finding canary còn cộng một điểm vào {@code unresolved_location_count} và làm bẩn chính
     * chỉ số đo sức khoẻ chuẩn hoá đường dẫn.
     */
    @Test
    void isCanaryEvidence_tachDung28CanhBaoNguoiDungKhoi6Canary() {
        List<WarningDraft> tatCa = new SpotbugsXmlAdapter().parse(SampleArtifacts.sandboxSpotbugsXml());

        List<WarningDraft> canary =
                tatCa.stream().filter(AnalyzerSanityChecker::isCanaryEvidence).toList();
        List<WarningDraft> nguoiDung = tatCa.stream()
                .filter(draft -> !AnalyzerSanityChecker.isCanaryEvidence(draft))
                .toList();

        assertThat(tatCa).hasSize(SampleArtifacts.expectedInt("spotbugs", "findings") + 6);
        assertThat(canary).hasSize(6);
        assertThat(nguoiDung).hasSize(SampleArtifacts.expectedInt("spotbugs", "findings"));
        assertThat(nguoiDung)
                .allSatisfy(draft -> assertThat(draft.rawFilePath()).doesNotContain("evidence/canary"));
    }
}
