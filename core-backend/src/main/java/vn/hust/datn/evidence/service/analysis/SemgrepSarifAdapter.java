package vn.hust.datn.evidence.service.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Đọc báo cáo SARIF 2.1.0 của Semgrep.
 *
 * <p>Bốn đặc điểm của SARIF thật ({@code docs/samples/vulnshop/semgrep.sarif}) khiến parser không
 * thể viết theo trực giác:
 *
 * <ol>
 *   <li><b>{@code result} KHÔNG có {@code level}.</b> Mức nghiêm trọng chỉ lấy được bằng cách join
 *       sang {@code tool.driver.rules[]} theo {@code ruleId}. Và {@code result.properties} là
 *       object RỖNG {@code {}} — {@code precision} nằm ở {@code rules[].properties.precision}.
 *   <li><b>{@code runs} là MẢNG.</b> Phải join rules theo TỪNG run, không gộp toàn cục — hai run
 *       có thể khai cùng một ruleId với level khác nhau.
 *   <li><b>{@code originalUriBaseIds} KHÔNG tồn tại</b> dù mọi {@code artifactLocation} khai
 *       {@code uriBaseId: "%SRCROOT%"}, và {@code uri} là đường dẫn tuyệt đối trong container
 *       ({@code /work/vulnshop/src/...}). Việc đối chiếu về snapshot là của
 *       {@link SnapshotPathResolver}; ở đây chỉ giữ nguyên văn.
 *   <li><b>{@code fingerprints["matchBasedId/v1"]} là chuỗi literal {@code "requires login"}</b> ở
 *       CẢ 5 result — không phải hash. Không lưu, và tuyệt đối không dùng làm khoá tự nhiên; khoá
 *       phân biệt là {@code source_index}.
 * </ol>
 */
@Component
public class SemgrepSarifAdapter implements StaticAnalysisAdapter {

    /** Tag CWE của Semgrep là chuỗi dạng {@code "CWE-328: Use of Weak Hash"}. */
    private static final Pattern CWE_TAG = Pattern.compile("^CWE-(\\d+)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WarningTool supportedTool() {
        return WarningTool.semgrep;
    }

    @Override
    public boolean looksLikeSupportedArtifact(byte[] rawArtifact) {
        if (rawArtifact == null || rawArtifact.length == 0) {
            return false;
        }
        int probeLength = Math.min(rawArtifact.length, 4096);
        // Bo khoang trang truoc khi so: SARIF that cua Semgrep khong pretty-print
        // ("version":"2.1.0" khong co dau cach), nhung cong cu khac co the co.
        String head = new String(rawArtifact, 0, probeLength, StandardCharsets.UTF_8)
                .replaceAll("\\s+", "");
        // KHONG kiem "$schema": SARIF that cua Semgrep OSS 1.170.1 khong he co khoa do, du dac ta
        // SARIF khuyen dung. Kiem theo cau truc bat buoc thuc su co: mot object JSON co "runs".
        return head.startsWith("{") && head.contains("\"runs\":[");
    }

    @Override
    public String toolVersion(byte[] rawArtifact) {
        JsonNode runs = readSarif(rawArtifact).path("runs");
        if (!runs.isArray() || runs.isEmpty()) {
            return null;
        }
        JsonNode version = runs.get(0).path("tool").path("driver").path("semanticVersion");
        return version.isMissingNode() ? null : version.asText();
    }

    @Override
    public List<WarningDraft> parse(byte[] rawArtifact) {
        JsonNode root = readSarif(rawArtifact);
        JsonNode runs = root.path("runs");
        if (!runs.isArray() || runs.isEmpty()) {
            throw new InvalidArtifactException("SARIF khong co 'runs' nao");
        }

        List<WarningDraft> drafts = new ArrayList<>();
        int sourceIndex = 0;
        for (JsonNode run : runs) {
            // Join rules theo TUNG run: hai run co the khai cung ruleId voi level khac nhau.
            Map<String, JsonNode> rulesById = indexRules(run);
            if (rulesById.isEmpty()) {
                // 0 rule nghia la ruleset khong nap duoc — ket qua van la mot SARIF hop le voi 0
                // finding, tuc la hong IM LANG. Chan tai day.
                throw new InvalidArtifactException(
                        "Semgrep khong nap duoc rule nao (tool.driver.rules rong). Nhieu kha nang "
                                + "ruleset vendor khong duoc mount vao container.");
            }
            for (JsonNode result : run.path("results")) {
                drafts.add(toDraft(result, rulesById, sourceIndex++));
            }
        }

        if (drafts.isEmpty()) {
            throw new InvalidArtifactException(
                    "Semgrep khong bao canh bao nao. 0 canh bao phai duoc coi la loi, khong duoc "
                            + "lang le ghi mot analysis_run rong.");
        }
        return drafts;
    }

    private JsonNode readSarif(byte[] rawArtifact) {
        try {
            return objectMapper.readTree(rawArtifact);
        } catch (IOException e) {
            throw new InvalidArtifactException("Khong doc duoc SARIF: " + e.getMessage(), e);
        }
    }

    private Map<String, JsonNode> indexRules(JsonNode run) {
        Map<String, JsonNode> rulesById = new HashMap<>();
        for (JsonNode rule : run.path("tool").path("driver").path("rules")) {
            String id = rule.path("id").asText(null);
            if (id != null) {
                rulesById.put(id, rule);
            }
        }
        return rulesById;
    }

    private WarningDraft toDraft(JsonNode result, Map<String, JsonNode> rulesById, int sourceIndex) {
        String ruleId = result.path("ruleId").asText(null);
        if (ruleId == null) {
            throw new InvalidArtifactException("SARIF result thu " + sourceIndex + " khong co ruleId");
        }
        // Rule khong co trong rules[] van phai ra evidence — mat severity chinh xac con hon mat
        // ca canh bao.
        JsonNode rule = rulesById.get(ruleId);
        String level = rule == null ? null : rule.path("defaultConfiguration").path("level").asText(null);
        String precision = rule == null ? null : rule.path("properties").path("precision").asText(null);

        JsonNode region = result.path("locations")
                .path(0)
                .path("physicalLocation")
                .path("region");
        JsonNode artifactLocation = result.path("locations")
                .path(0)
                .path("physicalLocation")
                .path("artifactLocation");

        String uriRaw = artifactLocation.path("uri").asText(null);
        Integer startLine = region.hasNonNull("startLine") ? region.get("startLine").asInt() : null;
        Integer endLine = region.hasNonNull("endLine") ? region.get("endLine").asInt() : null;
        String message = result.path("message").path("text").asText(null);
        WarningSeverity severity = severityOf(level, precision);
        String ruleIdNormalized = normalizeRuleId(ruleId);

        String summary = "[semgrep/%s] %s: %s — %s:%s"
                .formatted(
                        severity,
                        ruleIdNormalized,
                        firstSentence(message),
                        uriRaw,
                        startLine != null ? startLine : "?");

        List<WarningLocationDraft> locations = List.of(new WarningLocationDraft(
                0,
                null, // SARIF khong co khai niem role
                true, // SARIF chi co mot vi tri chinh, luon coi la primary
                uriRaw,
                startLine,
                endLine,
                null, // khong co logicalLocations -> khong biet class
                null));

        return new WarningDraft(
                EvidenceType.STATIC_WARNING,
                EvidenceSource.semgrep,
                uriRaw,
                startLine,
                endLine,
                // SARIF khong co logicalLocations nen KHONG BAO GIO co symbol — V2 da ghi ro.
                null,
                summary,
                contentOf(result, rule, ruleId, level, precision, region, artifactLocation, message),
                WarningTool.semgrep,
                ruleId,
                ruleIdNormalized,
                level,
                severity,
                cweIdsOf(rule),
                true,
                sourceIndex,
                locations);
    }

    /**
     * Mapping ở {@code docs/evidence-model.md} mục 4.
     *
     * <p>Nhánh {@code warning + precision khác very-high → LOW} KHÔNG có mẫu thật nào: cả 60/60
     * rule trong SARIF thật đều {@code precision: very-high}. Chỉ kiểm được bằng fixture tự chế —
     * ghi ở đây để người đọc không mất công đi tìm ví dụ thật.
     */
    private WarningSeverity severityOf(String level, String precision) {
        if (level == null) {
            return WarningSeverity.INFO;
        }
        return switch (level) {
            case "error" -> WarningSeverity.HIGH;
            case "warning" -> "very-high".equals(precision) ? WarningSeverity.MEDIUM : WarningSeverity.LOW;
            case "note" -> WarningSeverity.INFO;
            default -> WarningSeverity.INFO;
        };
    }

    /** {@code work.semgrep-rules.java.lang...use-of-md5.use-of-md5} → {@code use-of-md5}. */
    private String normalizeRuleId(String ruleId) {
        int lastDot = ruleId.lastIndexOf('.');
        return lastDot < 0 ? ruleId : ruleId.substring(lastDot + 1);
    }

    private List<Integer> cweIdsOf(JsonNode rule) {
        if (rule == null) {
            return null;
        }
        List<Integer> cweIds = new ArrayList<>();
        for (JsonNode tag : rule.path("properties").path("tags")) {
            Matcher matcher = CWE_TAG.matcher(tag.asText(""));
            if (matcher.find()) {
                cweIds.add(Integer.valueOf(matcher.group(1)));
            }
        }
        return cweIds.isEmpty() ? null : cweIds;
    }

    private String firstSentence(String message) {
        if (message == null || message.isBlank()) {
            return "(khong co thong diep)";
        }
        String oneLine = message.replaceAll("\\s+", " ").trim();
        int dot = oneLine.indexOf(". ");
        String sentence = dot > 0 ? oneLine.substring(0, dot) : oneLine;
        return sentence.length() > 200 ? sentence.substring(0, 200) + "…" : sentence;
    }

    /**
     * KHÔNG đưa vào: {@code fingerprints} (literal {@code "requires login"} ở cả 5 result — lưu
     * vào là mời người sau dùng nó làm khoá tự nhiên) và mảng {@code rules[]} 60 phần tử.
     */
    private Map<String, Object> contentOf(
            JsonNode result,
            JsonNode rule,
            String ruleId,
            String level,
            String precision,
            JsonNode region,
            JsonNode artifactLocation,
            String message) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tool", "semgrep");
        content.put("rule_id", ruleId);
        content.put("level", level);
        content.put("precision", precision);
        content.put("tags", tagsOf(rule));
        content.put("message", message);
        content.put("snippet", snippetOf(region));
        content.put("start_column", region.hasNonNull("startColumn") ? region.get("startColumn").asInt() : null);
        content.put("end_column", region.hasNonNull("endColumn") ? region.get("endColumn").asInt() : null);
        content.put("uri_raw", artifactLocation.path("uri").asText(null));
        content.put("uri_base_id", artifactLocation.path("uriBaseId").asText(null));
        content.put("help_uri", rule == null ? null : rule.path("helpUri").asText(null));
        return content;
    }

    private List<String> tagsOf(JsonNode rule) {
        if (rule == null) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : rule.path("properties").path("tags")) {
            tags.add(tag.asText());
        }
        return tags;
    }

    private String snippetOf(JsonNode region) {
        String snippet = region.path("snippet").path("text").asText(null);
        if (snippet == null) {
            return null;
        }
        return snippet.length() > 2000 ? snippet.substring(0, 2000) : snippet;
    }
}
