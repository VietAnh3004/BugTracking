package vn.hust.datn.evidence.service.analysis.sandbox;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.service.analysis.EvidenceDraft;
import vn.hust.datn.evidence.service.analysis.XmlParsers;

/**
 * Bốn tầng chống "chạy xong nhưng 0 cảnh báo" (ADR 0008 mục 4), áp trên artifact mà sandbox vừa
 * thu về.
 *
 * <h2>Bốn tầng KHÔNG thay thế nhau</h2>
 *
 * Mỗi tầng bắt một kiểu hỏng mà ba tầng kia mù. Bằng chứng cụ thể nhất là ca "thiếu
 * {@code -auxclasspath}" đo được ở {@code docs/spike-spotbugs-cli.md}: SpotBugs mất 3/28 cảnh báo
 * mà vẫn exit 0, XML vẫn hợp lệ, {@code total_classes} vẫn đúng 20 — nó lọt qua CẢ tầng 2 lẫn
 * canary (canary nằm trong image nên không phụ thuộc classpath của người dùng), chỉ tầng 1 bắt
 * được, và chỉ bắt được nhờ tầng 1 bao gồm cả các lệnh CHUẨN BỊ chứ không riêng lệnh analyzer.
 *
 * <p>Lớp này lo tầng 1-3. Tầng 4 (quyết định trạng thái run) là của
 * {@code AnalysisRunService.decide}.
 *
 * <h2>KHÔNG kiểm {@code missingClasses}</h2>
 *
 * Nó là tín hiệu TƯƠNG ĐỐI: 6 là sàn của chính vulnshop và nhảy lên 13 khi thiếu auxclasspath,
 * nhưng mọi ngưỡng tuyệt đối đều là báo động giả cho repo khác. Nó chỉ có nghĩa khi so hai lần
 * chạy của cùng một repo.
 */
@Service
public class AnalyzerSanityChecker {

    /** Đường dẫn nguồn của mã canary trong image ({@code /opt/canary/src/evidence/canary/}). */
    public static final String CANARY_PATH_PREFIX = "evidence/canary/";

    private static final String CANARY_CLASS_PREFIX = "evidence.canary.";

    private static final String FINDSECBUGS_PLUGIN_ID = "com.h3xstream.findsecbugs";

    /**
     * Canary phải phủ CẢ HAI bộ rule. Thiếu một bộ là hai kiểu hỏng khác hẳn nhau: mất
     * find-sec-bugs là mất toàn bộ SQL injection / weak crypto / path traversal, còn mất SpotBugs
     * core nghĩa là engine gốc hỏng trong khi plugin vẫn nạp được.
     */
    private static final Set<String> CANARY_FINDSECBUGS = Set.of("WEAK_MESSAGE_DIGEST_MD5", "SQL_INJECTION_JDBC");

    private static final Set<String> CANARY_CORE = Set.of("DM_DEFAULT_ENCODING");

    /** ADR 0003 thành assertion trên DỮ LIỆU, không chỉ một câu trong tài liệu. */
    private static final String REQUIRED_JAVA_VERSION_PREFIX = "21.";

    public static final String SPOTBUGS_ARTIFACT = "spotbugs.xml";
    public static final String SEMGREP_ARTIFACT = "semgrep.sarif";
    public static final String JACOCO_ARTIFACT = "jacoco.xml";
    public static final String SUREFIRE_PREFIX = "surefire-reports/";

    private final ObjectMapper objectMapper;

    public AnalyzerSanityChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Bằng chứng sinh từ mã canary — phải LOẠI trước khi materialize.
     *
     * <p>Không chỉ vì {@code finding_count} sẽ sai: đường dẫn {@code evidence/canary/Canary.java}
     * không tồn tại trong snapshot người dùng, nên mỗi finding canary còn cộng một điểm vào
     * {@code unresolved_location_count} và làm bẩn chính chỉ số đo sức khoẻ chuẩn hoá đường dẫn.
     */
    public static boolean isCanaryEvidence(EvidenceDraft draft) {
        String path = draft.rawFilePath();
        if (path != null && path.startsWith(CANARY_PATH_PREFIX)) {
            return true;
        }
        String symbol = draft.symbol();
        return symbol != null && symbol.startsWith(CANARY_CLASS_PREFIX);
    }

    public SanityVerdict check(Map<String, byte[]> artifacts) {
        // --- Tầng 1: artifact tồn tại, khác rỗng ---
        String thieu = missingArtifact(artifacts);
        if (thieu != null) {
            return SanityVerdict.failed(thieu, false, 0);
        }

        Element bugCollection;
        try {
            bugCollection = XmlParsers.parseStrictNoDoctype(artifacts.get(SPOTBUGS_ARTIFACT))
                    .getDocumentElement();
        } catch (RuntimeException e) {
            return SanityVerdict.failed("spotbugs.xml khong parse duoc: " + e.getMessage(), false, 0);
        }

        // --- Tầng 3 tính trước để mọi nhánh thất bại đều báo được số canary ---
        Set<String> canaryTypes = new HashSet<>();
        int canaryCount = 0;
        for (Element bug : XmlParsers.directChildren(bugCollection, "BugInstance")) {
            if (isCanaryBug(bug)) {
                canaryTypes.add(XmlParsers.attr(bug, "type"));
                canaryCount++;
            }
        }
        boolean canaryOk = canaryTypes.containsAll(CANARY_FINDSECBUGS) && canaryTypes.containsAll(CANARY_CORE);

        // --- Tầng 2: locator cấu trúc trong chính artifact ---
        String loiCauTruc = structuralFailure(bugCollection, artifacts.get(SEMGREP_ARTIFACT));
        if (loiCauTruc != null) {
            return SanityVerdict.failed(loiCauTruc, canaryOk, canaryCount);
        }

        // --- Tầng 3: kết luận ---
        if (!canaryOk) {
            Set<String> thieuRule = new TreeSet<>();
            CANARY_FINDSECBUGS.stream().filter(type -> !canaryTypes.contains(type)).forEach(thieuRule::add);
            CANARY_CORE.stream().filter(type -> !canaryTypes.contains(type)).forEach(thieuRule::add);
            return SanityVerdict.failed(
                    "Canary khong ra du finding: thieu %s. Bo rule khong nap duoc hoac analyzer khong that su chay."
                            .formatted(thieuRule),
                    false,
                    canaryCount);
        }

        return SanityVerdict.passed(canaryCount);
    }

    /** Thiếu file ⇒ FAILED, KHÔNG phải "0 finding". Đây là ranh giới quan trọng nhất của tầng 1. */
    private String missingArtifact(Map<String, byte[]> artifacts) {
        for (String required : new String[] {SPOTBUGS_ARTIFACT, SEMGREP_ARTIFACT, JACOCO_ARTIFACT}) {
            byte[] content = artifacts.get(required);
            if (content == null || content.length == 0) {
                return "Sandbox khong sinh ra '%s' (hoac file rong) — day la run FAILED, khong phai '0 canh bao'"
                        .formatted(required);
            }
        }
        boolean coBaoCaoTest = artifacts.keySet().stream().anyMatch(name -> name.startsWith(SUREFIRE_PREFIX));
        if (!coBaoCaoTest) {
            return "Sandbox khong sinh ra bao cao Surefire nao — test khong chay duoc";
        }
        return null;
    }

    private String structuralFailure(Element bugCollection, byte[] semgrepSarif) {
        Element summary = XmlParsers.firstDirectChild(bugCollection, "FindBugsSummary");
        if (summary == null) {
            return "spotbugs.xml khong co <FindBugsSummary>";
        }

        Integer totalClasses = XmlParsers.intAttr(summary, "total_classes");
        if (totalClasses == null || totalClasses <= 0) {
            return "SpotBugs quet 0 class — no co chay nhung khong doc duoc bytecode nao cua nguoi dung";
        }

        String javaVersion = XmlParsers.attr(summary, "java_version");
        if (javaVersion == null || !javaVersion.startsWith(REQUIRED_JAVA_VERSION_PREFIX)) {
            return "SpotBugs chay tren JDK '%s', phai la 21.x — JDK moi hon cho 0 canh bao ma van bao thanh cong"
                    .formatted(javaVersion);
        }

        Element errors = XmlParsers.firstDirectChild(bugCollection, "Errors");
        Integer errorCount = errors == null ? null : XmlParsers.intAttr(errors, "errors");
        if (errorCount != null && errorCount > 0) {
            return "SpotBugs bao %d loi khi phan tich".formatted(errorCount);
        }

        boolean coFindSecBugs = false;
        for (Element plugin : elementsByTag(bugCollection, "Plugin")) {
            if (FINDSECBUGS_PLUGIN_ID.equals(XmlParsers.attr(plugin, "id"))) {
                coFindSecBugs = true;
                break;
            }
        }
        if (!coFindSecBugs) {
            return "find-sec-bugs khong duoc nap — mat toan bo rule SQL injection / weak crypto / path traversal";
        }

        return semgrepStructuralFailure(semgrepSarif);
    }

    private String semgrepStructuralFailure(byte[] sarif) {
        JsonNode root;
        try {
            root = objectMapper.readTree(sarif);
        } catch (Exception e) {
            return "semgrep.sarif khong parse duoc: " + e.getMessage();
        }

        JsonNode runs = root.path("runs");
        if (!runs.isArray() || runs.isEmpty()) {
            return "semgrep.sarif khong co run nao";
        }
        for (JsonNode run : runs) {
            JsonNode invocations = run.path("invocations");
            if (invocations.isArray()
                    && !invocations.isEmpty()
                    && !invocations.get(0).path("executionSuccessful").asBoolean(false)) {
                return "Semgrep bao executionSuccessful=false";
            }
            // 0 rule = ruleset khong doc duoc, ma SARIF thi van hoan toan hop le — dung kieu hong
            // im lang can chan.
            if (run.path("tool").path("driver").path("rules").size() == 0) {
                return "Semgrep nap 0 rule — ruleset khong doc duoc, SARIF van hop le";
            }
        }
        return null;
    }

    private boolean isCanaryBug(Element bug) {
        for (Element clazz : elementsByTag(bug, "Class")) {
            String className = XmlParsers.attr(clazz, "classname");
            if (className != null && className.startsWith(CANARY_CLASS_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Duyệt ĐỆ QUY có chủ đích ở đây: {@code <Plugin>} nằm trong {@code <FindBugsSummary>} và
     * {@code <Class>} nằm sâu trong {@code <BugInstance>}. Khác hẳn chỗ đếm vị trí cảnh báo, nơi
     * bắt buộc phải dùng {@code directChildren} (xem cạm bẫy số 1 của tuần 8-9).
     */
    private Iterable<Element> elementsByTag(Element parent, String tagName) {
        org.w3c.dom.NodeList nodes = parent.getElementsByTagName(tagName);
        java.util.List<Element> elements = new java.util.ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }
}
