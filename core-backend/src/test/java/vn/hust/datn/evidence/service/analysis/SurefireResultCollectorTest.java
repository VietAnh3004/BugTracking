package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.domain.TestFailureKind;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/** Chạy trên 9 file {@code TEST-*.xml} THẬT của vulnshop (33 test, 3 failure + 2 error). */
class SurefireResultCollectorTest {

    private final SurefireResultCollector collector = new SurefireResultCollector();

    private List<EvidenceDraft> collectAll() {
        List<EvidenceDraft> drafts = new ArrayList<>();
        SampleArtifacts.surefireReports().forEach(path -> {
            try {
                drafts.addAll(collector.collect(Files.readAllBytes(path)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return drafts;
    }

    @Test
    void collect_chinFileThat_dungSoTestTheoExpectedAnalysis() {
        List<TestCaseDraft> testCases = collectAll().stream()
                .filter(TestCaseDraft.class::isInstance)
                .map(TestCaseDraft.class::cast)
                .toList();

        assertThat(testCases).hasSize(SampleArtifacts.expectedInt("tests", "total"));
        assertThat(testCases.stream()
                        .filter(draft -> draft.status() == TestCaseStatus.FAILED)
                        .count())
                .isEqualTo(SampleArtifacts.expectedInt("tests", "failed"));
    }

    /**
     * {@code <error>} là ngoại lệ, {@code <failure>} là assertion — tên hai thẻ ngược trực giác.
     * Phân biệt sai làm hỏng toàn bộ liên kết bằng chứng vì chỉ kiểu ngoại lệ mới có frame
     * production.
     */
    @Test
    void collect_theErrorLaNgoaiLeTheFailureLaAssertion() {
        List<TestCaseDraft> failed = collectAll().stream()
                .filter(TestCaseDraft.class::isInstance)
                .map(TestCaseDraft.class::cast)
                .filter(draft -> draft.status() == TestCaseStatus.FAILED)
                .toList();

        assertThat(failed.stream()
                        .filter(draft -> draft.failureKind() == TestFailureKind.ERROR)
                        .count())
                .isEqualTo(3);
        assertThat(failed.stream()
                        .filter(draft -> draft.failureKind() == TestFailureKind.FAILURE)
                        .count())
                .isEqualTo(3);
        // Ngoai le den tu ma production, assertion den tu lop test. Hai NPE: mot cua TP-11
        // (emailDomainOf) va mot cua TP-16 (displayNameOf).
        assertThat(failed.stream()
                        .filter(draft -> draft.failureKind() == TestFailureKind.ERROR)
                        .map(TestCaseDraft::failureType))
                .containsExactlyInAnyOrder(
                        "java.lang.ArithmeticException",
                        "java.lang.NullPointerException",
                        "java.lang.NullPointerException");
    }

    /**
     * Phát hiện 2 (SEEDED.md §5) mã hoá thành test: chỉ 3/6 test hỏng có frame trỏ về mã
     * production. Ai "sửa" heuristic cho ra 6/6 thì test này đỏ.
     *
     * <p>Con số đổi từ 2/5 lên 3/6 ở tuần 10-11 khi gieo TP-16 — xem SEEDED.md. Chú ý: có frame
     * production là điều kiện CẦN chứ chưa đủ để EvidenceLinker tầng 3 sinh liên kết; frame còn
     * phải rơi vào một dòng ĐÃ CÓ CẢNH BÁO. Trước TP-16 thì cả hai frame production của vulnshop
     * đều thuộc nhóm "không công cụ nào bắt", nên tầng 3 chạy đúng mà sinh 0 liên kết.
     */
    @Test
    void collect_chiBaTrongSauTestHongCoFrameProduction() {
        List<StackTraceDraft> traces = collectAll().stream()
                .filter(StackTraceDraft.class::isInstance)
                .map(StackTraceDraft.class::cast)
                .toList();

        assertThat(traces).hasSize(6);

        List<StackTraceDraft> withProduction = traces.stream()
                .filter(trace -> Boolean.TRUE.equals(trace.content().get("has_production_frame")))
                .toList();

        assertThat(withProduction).hasSize(3);
        assertThat(withProduction).extracting(StackTraceDraft::rawFilePath)
                .containsExactlyInAnyOrder(
                        "vn/hust/datn/casestudy/vulnshop/cart/CartService.java",
                        "vn/hust/datn/casestudy/vulnshop/user/UserService.java",
                        "vn/hust/datn/casestudy/vulnshop/user/UserService.java");
        // 66 la TP-16: dong DUY NHAT cua vulnshop vua co canh bao tinh vua co frame stack trace.
        assertThat(withProduction).extracting(StackTraceDraft::startLine).containsExactlyInAnyOrder(25, 30, 66);

        // Ba trace con lai phai NOI THANG ra trong summary rang khong co frame production, thay vi
        // de LLM suy dien tu su vang mat cua du lieu.
        assertThat(traces.stream()
                        .filter(trace -> !Boolean.TRUE.equals(trace.content().get("has_production_frame")))
                        .map(StackTraceDraft::summary))
                .hasSize(3)
                // Ba trace kieu assertion: PaginationHelperTest, ProductTest, OrderStatusCheckerTest.
                .allSatisfy(summary -> assertThat(summary).contains("KHONG co frame nao tro ve ma production"));
    }

    /**
     * STACK_TRACE tách khỏi FAILED_TEST vì evidence_record chỉ có MỘT bộ (file_path, start_line):
     * của test là file test, của trace là frame production đầu tiên.
     */
    @Test
    void collect_testHong_sinhHaiEvidenceNoiVoiNhauQuaCorrelationId() {
        List<EvidenceDraft> drafts = collectAll();

        List<TestCaseDraft> failed = drafts.stream()
                .filter(TestCaseDraft.class::isInstance)
                .map(TestCaseDraft.class::cast)
                .filter(draft -> draft.status() == TestCaseStatus.FAILED)
                .toList();
        List<StackTraceDraft> traces = drafts.stream()
                .filter(StackTraceDraft.class::isInstance)
                .map(StackTraceDraft.class::cast)
                .toList();

        assertThat(failed).extracting(TestCaseDraft::traceCorrelationId).doesNotContainNull();
        assertThat(traces).extracting(StackTraceDraft::traceCorrelationId)
                .containsExactlyInAnyOrderElementsOf(
                        failed.stream().map(TestCaseDraft::traceCorrelationId).toList());

        // Test xanh khong sinh trace va khong co correlation id.
        assertThat(drafts.stream()
                        .filter(TestCaseDraft.class::isInstance)
                        .map(TestCaseDraft.class::cast)
                        .filter(draft -> draft.status() == TestCaseStatus.PASSED))
                .allSatisfy(draft -> assertThat(draft.traceCorrelationId()).isNull());
    }

    /**
     * 56 thẻ {@code <property>} mỗi file chứa {@code java.class.path} với đường dẫn Windows tuyệt
     * đối và {@code user.name}. Lọt vào content là chui thẳng vào prompt LLM và vào báo cáo.
     */
    @Test
    void collect_khongDuaPropertyRacVaoContent() {
        String allContent = collectAll().stream().map(draft -> draft.content().toString()).reduce("", String::concat);

        assertThat(allContent).doesNotContain("java.class.path");
        assertThat(allContent).doesNotContain("user.name");
        assertThat(allContent).doesNotContain("user.home");
        assertThat(allContent).doesNotContain("D:\\");
        assertThat(allContent).doesNotContain("C:\\");
    }

    @Test
    void collect_evidenceTypePhanBietTestXanhVaTestHong() {
        List<EvidenceDraft> drafts = collectAll();

        // 35 test = 29 xanh + 6 hong; moi test hong sinh THEM mot STACK_TRACE rieng.
        assertThat(drafts.stream().filter(d -> d.type() == EvidenceType.TEST_RESULT).count())
                .isEqualTo(29);
        assertThat(drafts.stream().filter(d -> d.type() == EvidenceType.FAILED_TEST).count())
                .isEqualTo(6);
        assertThat(drafts.stream().filter(d -> d.type() == EvidenceType.STACK_TRACE).count())
                .isEqualTo(6);
        assertThat(drafts).hasSize(41);
    }

    /** Test tích hợp có {@code <system-out>} rất dài mà vẫn PASSED — không được nhầm là hỏng. */
    @Test
    void collect_testCoSystemOut_khongNhamLaHong() {
        List<TestCaseDraft> integrationTests = collectAll().stream()
                .filter(TestCaseDraft.class::isInstance)
                .map(TestCaseDraft.class::cast)
                .filter(draft -> draft.className().endsWith("UserRepositoryIntegrationTest"))
                .toList();

        assertThat(integrationTests).isNotEmpty();
        assertThat(integrationTests).allSatisfy(draft -> assertThat(draft.status()).isEqualTo(TestCaseStatus.PASSED));
    }

    @Test
    void collect_tenTestThamSoHoa_methodNameBoHauToNgoacVuong() {
        byte[] xml = suiteWith("<testcase name=\"tinhTong[1]\" classname=\"vn.hust.app.FooTest\" time=\"0.01\"/>");

        TestCaseDraft draft = (TestCaseDraft) collector.collect(xml).get(0);

        assertThat(draft.displayName()).isEqualTo("tinhTong[1]");
        assertThat(draft.methodName()).isEqualTo("tinhTong");
    }

    @Test
    void collect_timeDungDauPhay_vanDoiDuocSangMiliGiay() {
        byte[] xml = suiteWith("<testcase name=\"a\" classname=\"vn.hust.app.FooTest\" time=\"0,024\"/>");

        assertThat(((TestCaseDraft) collector.collect(xml).get(0)).durationMs()).isEqualTo(24);
    }

    @Test
    void collect_testSkipped_statusSkippedVaKhongCoFailureKind() {
        byte[] xml = suiteWith(
                "<testcase name=\"a\" classname=\"vn.hust.app.FooTest\" time=\"0.01\"><skipped/></testcase>");

        TestCaseDraft draft = (TestCaseDraft) collector.collect(xml).get(0);

        assertThat(draft.status()).isEqualTo(TestCaseStatus.SKIPPED);
        assertThat(draft.failureKind()).isNull();
        assertThat(draft.type()).isEqualTo(EvidenceType.TEST_RESULT);
    }

    @Test
    void collect_khongCoTestcaseNao_nemLoi() {
        assertThatThrownBy(() -> collector.collect(suiteWith("")))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("khong co testcase");
    }

    /**
     * Artifact là dữ liệu KHÔNG tin cậy: từ giai đoạn D nó do analyzer chạy trên repo người dùng
     * upload sinh ra. Thiếu thuộc tính bắt buộc phải cho lỗi ĐỌC ĐƯỢC (→ run FAILED kèm lý do),
     * không phải NullPointerException (→ 500 và một stack trace vô nghĩa).
     */
    @Test
    void collect_testcaseThieuThuocTinhBatBuoc_nemLoiDocDuocChuKhongNpe() {
        byte[] thieuClassname = suiteWith("<testcase name=\"a\" time=\"0.1\"/>");
        byte[] thieuName = suiteWith("<testcase classname=\"vn.hust.app.FooTest\" time=\"0.1\"/>");

        assertThatThrownBy(() -> collector.collect(thieuClassname))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("thieu thuoc tinh bat buoc");
        assertThatThrownBy(() -> collector.collect(thieuName)).isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void collect_khongPhaiBaoCaoSurefire_nemLoi() {
        assertThatThrownBy(() -> collector.collect("<report/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void looksLikeSupportedArtifact_phanBietDuocDinhDang() {
        byte[] real = readFirstReport();
        assertThat(collector.looksLikeSupportedArtifact(real)).isTrue();
        assertThat(collector.looksLikeSupportedArtifact(SampleArtifacts.jacocoXml())).isFalse();
        assertThat(collector.looksLikeSupportedArtifact(new byte[0])).isFalse();
    }

    @Test
    void toolVersion_docTuChinhArtifact() {
        assertThat(collector.toolVersion(readFirstReport())).isEqualTo("3.0.2");
    }

    private static byte[] readFirstReport() {
        try {
            return Files.readAllBytes(SampleArtifacts.surefireReports().get(0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] suiteWith(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite version="3.0.2" name="vn.hust.app.FooTest" time="0.1" tests="1" errors="0" skipped="0" failures="0">
                %s
                </testsuite>
                """
                .formatted(body)
                .getBytes(StandardCharsets.UTF_8);
    }
}
