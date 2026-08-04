package vn.hust.datn.evidence.service.analysis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.StackFrameKind;
import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.domain.TestFailureKind;

/**
 * Đọc MỘT file {@code TEST-*.xml} của Maven Surefire.
 *
 * <p>Nhận một file chứ không phải cả thư mục: ghép nhiều file là việc của tầng điều phối, giữ
 * collector thuần hàm và test được từng file một.
 *
 * <p>Ba điều dễ sai, đều thành test:
 *
 * <ul>
 *   <li>{@code <error>} là NGOẠI LỆ thoát ra, {@code <failure>} là ASSERTION thất bại — tên hai
 *       thẻ ngược trực giác. Phân biệt sai là hỏng toàn bộ liên kết bằng chứng, vì chỉ kiểu ngoại
 *       lệ mới có frame trỏ về mã production (2/5 test hỏng thật).
 *   <li>Mỗi file có <b>56 thẻ {@code <property>}</b> chứa {@code java.class.path} dài hàng KB với
 *       đường dẫn Windows tuyệt đối, {@code user.name}, {@code user.home}. Chúng KHÔNG được vào
 *       {@code content} — mọi thứ trong content đi thẳng vào prompt LLM và vào báo cáo.
 *   <li>{@code @time} là GIÂY thập phân, cột lưu là mili giây; và có locale ghi bằng dấu phẩy.
 * </ul>
 */
@Component
public class SurefireResultCollector implements TestEvidenceCollector {

    @Override
    public EvidenceSource supportedSource() {
        return EvidenceSource.surefire;
    }

    @Override
    public boolean looksLikeSupportedArtifact(byte[] rawArtifact) {
        if (rawArtifact == null || rawArtifact.length == 0) {
            return false;
        }
        int probeLength = Math.min(rawArtifact.length, 4096);
        return new String(rawArtifact, 0, probeLength, StandardCharsets.UTF_8).contains("<testsuite");
    }

    @Override
    public String toolVersion(byte[] rawArtifact) {
        return XmlParsers.attr(XmlParsers.parseStrictNoDoctype(rawArtifact).getDocumentElement(), "version");
    }

    @Override
    public List<EvidenceDraft> collect(byte[] rawArtifact) {
        Element suite = XmlParsers.parseStrictNoDoctype(rawArtifact).getDocumentElement();
        if (suite == null || !"testsuite".equals(suite.getNodeName())) {
            throw new InvalidArtifactException("Khong phai bao cao Surefire: the goc khong phai <testsuite>");
        }

        String suiteName = XmlParsers.attr(suite, "name");
        String reportFile = "TEST-" + suiteName + ".xml";
        List<Element> testcases = XmlParsers.directChildren(suite, "testcase");
        if (testcases.isEmpty()) {
            throw new InvalidArtifactException("Bao cao Surefire '%s' khong co testcase nao".formatted(reportFile));
        }

        List<EvidenceDraft> drafts = new ArrayList<>();
        for (Element testcase : testcases) {
            collectOne(testcase, suite, suiteName, reportFile, drafts);
        }
        return drafts;
    }

    private void collectOne(
            Element testcase, Element suite, String suiteName, String reportFile, List<EvidenceDraft> drafts) {
        String className = XmlParsers.attr(testcase, "classname");
        String displayName = XmlParsers.attr(testcase, "name");
        // Artifact la du lieu KHONG TIN CAY: tu giai doan D no duoc sinh ra boi analyzer chay tren
        // repo nguoi dung upload. Thieu thuoc tinh bat buoc phai cho loi DOC DUOC (-> run FAILED
        // kem ly do), khong phai NullPointerException (-> 500 va mot dong stack trace vo nghia).
        if (className == null || displayName == null) {
            throw new InvalidArtifactException(
                    "Bao cao Surefire '%s' co <testcase> thieu thuoc tinh bat buoc (classname='%s', name='%s')"
                            .formatted(reportFile, className, displayName));
        }
        Integer durationMs = durationMsOf(XmlParsers.attr(testcase, "time"));

        // <error> = ngoai le, <failure> = assertion. Ten hai the NGUOC truc giac.
        Element error = XmlParsers.firstDirectChild(testcase, "error");
        Element failure = XmlParsers.firstDirectChild(testcase, "failure");
        Element skipped = XmlParsers.firstDirectChild(testcase, "skipped");

        TestCaseStatus status;
        TestFailureKind failureKind = null;
        Element problem = null;
        if (error != null) {
            status = TestCaseStatus.FAILED;
            failureKind = TestFailureKind.ERROR;
            problem = error;
        } else if (failure != null) {
            status = TestCaseStatus.FAILED;
            failureKind = TestFailureKind.FAILURE;
            problem = failure;
        } else if (skipped != null) {
            status = TestCaseStatus.SKIPPED;
        } else {
            status = TestCaseStatus.PASSED;
        }

        String methodName = methodNameOf(displayName);
        String symbol = className + "#" + methodName;
        String correlationId = status == TestCaseStatus.FAILED ? reportFile + "#" + displayName : null;
        String stackTraceRaw = problem == null ? null : textOf(problem);
        String failureType = problem == null ? null : XmlParsers.attr(problem, "type");
        String failureMessage = problem == null ? null : XmlParsers.attr(problem, "message");

        drafts.add(new TestCaseDraft(
                status == TestCaseStatus.FAILED ? EvidenceType.FAILED_TEST : EvidenceType.TEST_RESULT,
                EvidenceSource.surefire,
                // Duong dan file TEST suy tu FQCN — Surefire khong cung cap path nao.
                className.replace('.', '/') + ".java",
                null,
                null,
                symbol,
                summaryOfTestCase(status, failureKind, className, methodName, failureType, failureMessage, durationMs),
                contentOfTestCase(
                        suite, suiteName, className, methodName, status, failureKind, failureType, failureMessage,
                        durationMs, reportFile),
                className,
                methodName,
                displayName,
                status,
                failureKind,
                failureType,
                failureMessage,
                stackTraceRaw,
                durationMs,
                reportFile,
                correlationId));

        if (status == TestCaseStatus.FAILED) {
            drafts.add(stackTraceDraft(stackTraceRaw, className, methodName, failureType, failureMessage, correlationId));
        }
    }

    private StackTraceDraft stackTraceDraft(
            String stackTraceRaw,
            String className,
            String methodName,
            String failureType,
            String failureMessage,
            String correlationId) {
        List<StackFrameDraft> frames = StackTraceParser.parse(stackTraceRaw, className);
        StackFrameDraft firstProduction = frames.stream()
                .filter(frame -> frame.frameKind() == StackFrameKind.PRODUCTION)
                .findFirst()
                .orElse(null);

        long productionCount = frames.stream()
                .filter(frame -> frame.frameKind() == StackFrameKind.PRODUCTION)
                .count();
        long testCount =
                frames.stream().filter(frame -> frame.frameKind() == StackFrameKind.TEST).count();

        String summary;
        if (firstProduction != null) {
            summary = "Stack trace cua %s.%s — frame production dau tien: %s.%s (%s:%s)"
                    .formatted(
                            simpleName(className),
                            methodName,
                            simpleName(firstProduction.declaringClass()),
                            firstProduction.methodName(),
                            firstProduction.sourceFileName(),
                            firstProduction.lineNumber());
        } else {
            // 3/5 test hong that roi vao nhanh nay. Noi thang ra trong summary de LLM khong phai
            // suy dien tu su vang mat cua du lieu.
            summary = "Stack trace cua %s.%s — KHONG co frame nao tro ve ma production (%d frame, toan JUnit/JDK)"
                    .formatted(simpleName(className), methodName, frames.size());
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tool", "surefire");
        content.put("exception_type", failureType);
        content.put("exception_message", failureMessage);
        content.put("frame_count", frames.size());
        content.put("production_frame_count", productionCount);
        content.put("test_frame_count", testCount);
        content.put("has_production_frame", firstProduction != null);
        content.put("first_production_frame", firstProduction == null ? null : firstProduction.rawLine());
        // KHONG luu toan van trace o day — no da nam o test_case.stack_trace_raw, va danh sach
        // frame da nam o bang stack_frame. Nhan doi la tao hai nguon su that.

        return new StackTraceDraft(
                EvidenceType.STACK_TRACE,
                EvidenceSource.surefire,
                firstProduction == null ? null : firstProduction.rawFilePath(),
                firstProduction == null ? null : firstProduction.lineNumber(),
                firstProduction == null ? null : firstProduction.lineNumber(),
                firstProduction == null ? null : firstProduction.declaringClass() + "#" + firstProduction.methodName(),
                summary,
                content,
                correlationId,
                failureType,
                failureMessage,
                frames);
    }

    private String summaryOfTestCase(
            TestCaseStatus status,
            TestFailureKind failureKind,
            String className,
            String methodName,
            String failureType,
            String failureMessage,
            Integer durationMs) {
        return switch (status) {
            case PASSED -> "Test xanh: %s.%s (%d ms)".formatted(simpleName(className), methodName, orZero(durationMs));
            case SKIPPED -> "Test bi bo qua: %s.%s".formatted(simpleName(className), methodName);
            case FAILED -> "Test hong (%s): %s.%s — %s: %s"
                    .formatted(
                            failureKind,
                            simpleName(className),
                            methodName,
                            failureType,
                            failureMessage == null ? "(khong co thong diep)" : failureMessage);
        };
    }

    /**
     * KHÔNG đưa vào: 56 thẻ {@code <property>} (đường dẫn tuyệt đối máy dev + {@code user.name}),
     * {@code <system-out>}/{@code <system-err>} (không giới hạn kích thước — một test tích hợp có
     * cả log Testcontainers), và toàn văn stack trace (đã ở {@code test_case.stack_trace_raw}).
     */
    private Map<String, Object> contentOfTestCase(
            Element suite,
            String suiteName,
            String className,
            String methodName,
            TestCaseStatus status,
            TestFailureKind failureKind,
            String failureType,
            String failureMessage,
            Integer durationMs,
            String reportFile) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tool", "surefire");
        content.put("class_name", className);
        content.put("method_name", methodName);
        content.put("status", status.name());
        content.put("failure_kind", failureKind == null ? null : failureKind.name());
        content.put("failure_type", failureType);
        content.put("failure_message", failureMessage);
        content.put("duration_ms", durationMs);
        content.put("report_file", reportFile);
        Map<String, Object> suiteInfo = new LinkedHashMap<>();
        suiteInfo.put("name", suiteName);
        suiteInfo.put("tests", XmlParsers.intAttr(suite, "tests"));
        suiteInfo.put("failures", XmlParsers.intAttr(suite, "failures"));
        suiteInfo.put("errors", XmlParsers.intAttr(suite, "errors"));
        suiteInfo.put("skipped", XmlParsers.intAttr(suite, "skipped"));
        content.put("suite", suiteInfo);
        return content;
    }

    /** {@code ten[1]} của test tham số hoá → {@code ten}; {@code display_name} giữ nguyên bản đầy đủ. */
    private String methodNameOf(String displayName) {
        if (displayName == null) {
            return null;
        }
        int bracket = displayName.indexOf('[');
        return bracket > 0 ? displayName.substring(0, bracket) : displayName;
    }

    /** {@code @time} là GIÂY thập phân; có locale ghi bằng dấu phẩy. */
    private Integer durationMsOf(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(time.trim().replace(',', '.')) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOf(Element element) {
        String text = element.getTextContent();
        return text == null || text.isBlank() ? null : text.strip();
    }

    private String simpleName(String fqcn) {
        return fqcn == null ? null : fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
