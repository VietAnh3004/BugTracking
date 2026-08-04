package vn.hust.datn.evidence.service.analysis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Đọc {@code spotbugsXml.xml} của SpotBugs (kèm find-sec-bugs).
 *
 * <p>Ba điều rút ra từ artifact thật ({@code docs/samples/vulnshop/spotbugs.xml}, 28 BugInstance)
 * và được mã hoá thành test:
 *
 * <ol>
 *   <li>{@code <SourceLine>} xuất hiện ở BA tầng lồng nhau; chỉ con TRỰC TIẾP của
 *       {@code <BugInstance>} là vị trí cảnh báo (33 vị trí, không phải 90). Xem
 *       {@link XmlParsers#directChildren}.
 *   <li>{@code priority=1} là CAO NHẤT — trái trực giác so với mọi thang điểm khác.
 *   <li>5/28 BugInstance không có vị trí nào đánh dấu {@code primary='true'} (chỉ
 *       {@code synthetic='true'}), nên bắt buộc phải fallback về vị trí đầu tiên và ghi lại rằng
 *       đó là lựa chọn của ta.
 * </ol>
 */
@Component
public class SpotbugsXmlAdapter implements StaticAnalysisAdapter {

    @Override
    public WarningTool supportedTool() {
        return WarningTool.spotbugs;
    }

    @Override
    public boolean looksLikeSupportedArtifact(byte[] rawArtifact) {
        if (rawArtifact == null || rawArtifact.length == 0) {
            return false;
        }
        // Kiểm rẻ trên vài KB đầu, không parse cả file: file thật là MỘT dòng dài 111KB.
        int probeLength = Math.min(rawArtifact.length, 4096);
        String head = new String(rawArtifact, 0, probeLength, StandardCharsets.UTF_8);
        return head.contains("<BugCollection");
    }

    @Override
    public String toolVersion(byte[] rawArtifact) {
        return XmlParsers.attr(XmlParsers.parseStrictNoDoctype(rawArtifact).getDocumentElement(), "version");
    }

    @Override
    public List<WarningDraft> parse(byte[] rawArtifact) {
        Document document = XmlParsers.parseStrictNoDoctype(rawArtifact);
        Element root = document.getDocumentElement();
        if (root == null || !"BugCollection".equals(root.getNodeName())) {
            throw new InvalidArtifactException("Khong phai bao cao SpotBugs: the goc khong phai <BugCollection>");
        }

        List<Element> bugInstances = XmlParsers.directChildren(root, "BugInstance");
        if (bugInstances.isEmpty()) {
            throw new InvalidArtifactException(
                    "SpotBugs khong bao canh bao nao. 0 canh bao phai duoc coi la loi, khong "
                            + "duoc lang le ghi mot analysis_run rong — xem docs/spike-tuan-1.md.");
        }

        List<WarningDraft> drafts = new ArrayList<>(bugInstances.size());
        for (int index = 0; index < bugInstances.size(); index++) {
            drafts.add(toDraft(bugInstances.get(index), index));
        }
        return drafts;
    }

    private WarningDraft toDraft(Element bug, int sourceIndex) {
        String ruleId = XmlParsers.attr(bug, "type");
        String priority = XmlParsers.attr(bug, "priority");
        String shortMessage = XmlParsers.childText(bug, "ShortMessage");
        String longMessage = XmlParsers.childText(bug, "LongMessage");

        List<WarningLocationDraft> locations = locationsOf(bug);
        WarningLocationDraft primary = locations.stream()
                .filter(WarningLocationDraft::primary)
                .findFirst()
                // 5/28 BugInstance thật không có vị trí nào primary='true'.
                .orElse(locations.get(0));
        boolean hasPrimaryLocation = locations.stream().anyMatch(WarningLocationDraft::primary);

        WarningSeverity severity = severityOf(priority);
        String summary = "[spotbugs/%s] %s: %s — %s:%s"
                .formatted(
                        severity,
                        ruleId,
                        shortMessage != null ? shortMessage : "(khong co mo ta ngan)",
                        primary.pathRaw(),
                        primary.startLine() != null ? primary.startLine() : "?");

        return new WarningDraft(
                EvidenceType.STATIC_WARNING,
                EvidenceSource.spotbugs,
                primary.pathRaw(),
                primary.startLine(),
                primary.endLine(),
                symbolOf(bug),
                summary,
                contentOf(bug, ruleId, priority, shortMessage, longMessage, locations.size()),
                WarningTool.spotbugs,
                ruleId,
                // SpotBugs không có tiền tố rác nào để bỏ (khác Semgrep), nên hai cột bằng nhau.
                ruleId,
                priority,
                severity,
                cweIdsOf(bug),
                hasPrimaryLocation,
                sourceIndex,
                locations);
    }

    private List<WarningLocationDraft> locationsOf(Element bug) {
        // directChildren, KHONG getElementsByTagName — xem Javadoc XmlParsers.directChildren.
        List<Element> sourceLines = XmlParsers.directChildren(bug, "SourceLine");
        if (sourceLines.isEmpty()) {
            throw new InvalidArtifactException(
                    "BugInstance type='%s' khong co SourceLine con truc tiep nao".formatted(XmlParsers.attr(bug, "type")));
        }

        Element method = primaryOrFirst(XmlParsers.directChildren(bug, "Method"));
        List<WarningLocationDraft> locations = new ArrayList<>(sourceLines.size());
        for (int ordinal = 0; ordinal < sourceLines.size(); ordinal++) {
            Element line = sourceLines.get(ordinal);
            locations.add(new WarningLocationDraft(
                    ordinal,
                    XmlParsers.attr(line, "role"),
                    "true".equals(XmlParsers.attr(line, "primary")),
                    XmlParsers.attr(line, "sourcepath"),
                    XmlParsers.intAttr(line, "start"),
                    XmlParsers.intAttr(line, "end"),
                    XmlParsers.attr(line, "classname"),
                    method == null ? null : XmlParsers.attr(method, "name")));
        }
        return locations;
    }

    /**
     * Ánh xạ priority → severity. {@code priority=1} là CAO NHẤT: trái trực giác so với mọi thang
     * điểm khác, nên có test khẳng định tường minh cả ba nhánh.
     */
    private WarningSeverity severityOf(String priority) {
        if (priority == null) {
            return WarningSeverity.INFO;
        }
        return switch (priority.trim()) {
            case "1" -> WarningSeverity.HIGH;
            case "2" -> WarningSeverity.MEDIUM;
            case "3" -> WarningSeverity.LOW;
            default -> WarningSeverity.INFO;
        };
    }

    /** {@code FQCN#method}; 1/28 BugInstance thật không có {@code <Method>} nên chỉ còn class. */
    private String symbolOf(Element bug) {
        Element clazz = primaryOrFirst(XmlParsers.directChildren(bug, "Class"));
        if (clazz == null) {
            return null;
        }
        String className = XmlParsers.attr(clazz, "classname");
        Element method = primaryOrFirst(XmlParsers.directChildren(bug, "Method"));
        if (method == null) {
            return className;
        }
        return className + "#" + XmlParsers.attr(method, "name");
    }

    /** 8/28 BugInstance thật không có {@code @cweid} — trả null chứ không phải mảng rỗng. */
    private List<Integer> cweIdsOf(Element bug) {
        Integer cwe = XmlParsers.intAttr(bug, "cweid");
        return cwe == null ? null : List.of(cwe);
    }

    private Element primaryOrFirst(List<Element> elements) {
        if (elements.isEmpty()) {
            return null;
        }
        return elements.stream()
                .filter(element -> "true".equals(XmlParsers.attr(element, "primary")))
                .findFirst()
                .orElse(elements.get(0));
    }

    /**
     * Payload JSONB.
     *
     * <p>KHÔNG đưa vào: {@code <Project><Jar>} và ~40 {@code <AuxClasspathEntry>} (đường dẫn
     * tuyệt đối trên máy dev), {@code <BugPattern>}/{@code <BugCode>} (mô tả rule dạng HTML, tĩnh
     * theo rule — nhân bản 28 lần là lãng phí; nếu cần thì làm catalog rule riêng), và toàn văn
     * XML. Mọi thứ ở đây sẽ đi thẳng vào prompt LLM nên rác ở đây là rác trong prompt.
     */
    private Map<String, Object> contentOf(
            Element bug, String ruleId, String priority, String shortMessage, String longMessage, int locationCount) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tool", "spotbugs");
        content.put(
                "rule",
                Map.of(
                        "type", nullToEmpty(ruleId),
                        "abbrev", nullToEmpty(XmlParsers.attr(bug, "abbrev")),
                        "category", nullToEmpty(XmlParsers.attr(bug, "category"))));
        content.put("priority", priority);
        content.put("rank", XmlParsers.attr(bug, "rank"));
        content.put("short_message", shortMessage);
        content.put("long_message", longMessage);
        // Chỉ để đối chiếu tay với artifact gốc. KHONG dung lam khoa tu nhien: instanceHash trung
        // nhau giua cac BugInstance cung mau, con Semgrep thi fingerprint la chuoi literal giong
        // het nhau o ca 5 result.
        content.put("instance_hash", XmlParsers.attr(bug, "instanceHash"));
        content.put("location_count", locationCount);
        return content;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
