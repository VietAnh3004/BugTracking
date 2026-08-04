package vn.hust.datn.evidence.service.analysis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import vn.hust.datn.evidence.domain.CoverageStatus;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Đọc {@code jacoco.xml}.
 *
 * <p>Ba đặc điểm của định dạng khiến parser không viết theo trực giác được:
 *
 * <ol>
 *   <li><b>Có DOCTYPE trỏ tới {@code report.dtd} KHÔNG tồn tại.</b> Cách chống XXE thông thường
 *       ({@code disallow-doctype-decl=true}) sẽ từ chối chính file này, nên phải dùng
 *       {@link XmlParsers#parseAllowLocalDoctype} — cấm nạp DTD/entity ngoài nhưng chấp nhận
 *       DOCTYPE tồn tại.
 *   <li><b>Cấu trúc KÉP, không lồng nhau.</b> {@code package > class > method} (aggregate, không
 *       có số dòng) tách hoàn toàn khỏi {@code package > sourcefile > line} (số dòng, không biết
 *       method). Nối hai nhánh qua {@code class/@sourcefilename}.
 *   <li><b>{@code <sourcefile>} không có đường dẫn.</b> Phải ghép {@code package/@name} +
 *       {@code /} + {@code sourcefile/@name}.
 * </ol>
 *
 * <p>Và điều dễ "sửa nhầm" nhất: <b>"dòng được phủ" theo JaCoCo là {@code ci > 0}</b>, tức COVERED
 * CỘNG PARTIAL = 77 + 5 = 82 trên vulnshop, khớp counter {@code LINE covered='82'} của chính
 * JaCoCo và {@code expected-analysis.json}. Đếm mỗi COVERED ra 77 là sai.
 */
@Component
public class JacocoCoverageCollector implements TestEvidenceCollector {

    @Override
    public EvidenceSource supportedSource() {
        return EvidenceSource.jacoco;
    }

    @Override
    public boolean looksLikeSupportedArtifact(byte[] rawArtifact) {
        if (rawArtifact == null || rawArtifact.length == 0) {
            return false;
        }
        int probeLength = Math.min(rawArtifact.length, 4096);
        String head = new String(rawArtifact, 0, probeLength, StandardCharsets.UTF_8);
        return head.contains("<report") && head.contains("JACOCO");
    }

    /** JaCoCo KHÔNG khai phiên bản trong báo cáo — null ở đây là bình thường, không phải thiếu dữ liệu. */
    @Override
    public String toolVersion(byte[] rawArtifact) {
        return null;
    }

    @Override
    public List<EvidenceDraft> collect(byte[] rawArtifact) {
        Element report = XmlParsers.parseAllowLocalDoctype(rawArtifact).getDocumentElement();
        if (report == null || !"report".equals(report.getNodeName())) {
            throw new InvalidArtifactException("Khong phai bao cao JaCoCo: the goc khong phai <report>");
        }

        List<EvidenceDraft> drafts = new ArrayList<>();
        int totalLines = 0;
        for (Element pkg : XmlParsers.directChildren(report, "package")) {
            String packageName = XmlParsers.attr(pkg, "name");
            // Nhanh class/method tach hoan toan khoi nhanh sourcefile/line — noi qua @sourcefilename.
            Map<String, List<Element>> classesBySourceFile = new LinkedHashMap<>();
            for (Element clazz : XmlParsers.directChildren(pkg, "class")) {
                String sourceFileName = XmlParsers.attr(clazz, "sourcefilename");
                if (sourceFileName != null) {
                    classesBySourceFile
                            .computeIfAbsent(sourceFileName, key -> new ArrayList<>())
                            .add(clazz);
                }
            }

            for (Element sourceFile : XmlParsers.directChildren(pkg, "sourcefile")) {
                String sourceFileName = XmlParsers.attr(sourceFile, "name");
                List<CoverageLineDraft> lines = linesOf(sourceFile);
                totalLines += lines.size();
                if (lines.isEmpty()) {
                    // File khong co dong nao duoc instrument (vd lop chi co hang so). Bo qua thay
                    // vi tao mot evidence "do phu 0/0 dong" khong noi len dieu gi.
                    continue;
                }
                drafts.add(toDraft(
                        packageName,
                        sourceFileName,
                        lines,
                        classesBySourceFile.getOrDefault(sourceFileName, List.of())));
            }
        }

        if (totalLines == 0) {
            // jacoco.xml voi 0 dong nghia la agent khong attach duoc — rui ro that khi pom cua
            // nguoi dung tu dinh nghia argLine va de len -javaagent. Do KHONG phai "do phu 0%".
            throw new InvalidArtifactException(
                    "Bao cao JaCoCo khong co dong nao duoc instrument. Nhieu kha nang agent khong "
                            + "attach duoc (argLine bi ghi de), khong phai do phu 0%.");
        }
        return drafts;
    }

    private List<CoverageLineDraft> linesOf(Element sourceFile) {
        List<Element> lineElements = XmlParsers.directChildren(sourceFile, "line");
        List<CoverageLineDraft> lines = new ArrayList<>(lineElements.size());
        for (Element line : lineElements) {
            lines.add(new CoverageLineDraft(
                    orZero(XmlParsers.intAttr(line, "nr")),
                    orZero(XmlParsers.intAttr(line, "mi")),
                    orZero(XmlParsers.intAttr(line, "ci")),
                    orZero(XmlParsers.intAttr(line, "mb")),
                    orZero(XmlParsers.intAttr(line, "cb"))));
        }
        return lines;
    }

    private CoverageDraft toDraft(
            String packageName, String sourceFileName, List<CoverageLineDraft> lines, List<Element> classes) {
        List<CoverageMethodDraft> methods = new ArrayList<>();
        for (Element clazz : classes) {
            String className = XmlParsers.attr(clazz, "name");
            for (Element method : XmlParsers.directChildren(clazz, "method")) {
                methods.add(new CoverageMethodDraft(
                        className,
                        XmlParsers.attr(method, "name"),
                        XmlParsers.attr(method, "desc"),
                        XmlParsers.intAttr(method, "line"),
                        counter(method, "INSTRUCTION", "missed"),
                        counter(method, "INSTRUCTION", "covered"),
                        counter(method, "BRANCH", "missed"),
                        counter(method, "BRANCH", "covered"),
                        counter(method, "LINE", "missed"),
                        counter(method, "LINE", "covered")));
            }
        }

        long executed = lines.stream().filter(CoverageLineDraft::executed).count();
        long partial = lines.stream()
                .filter(line -> line.status() == CoverageStatus.PARTIAL)
                .count();
        long missed = lines.size() - executed;

        String rawFilePath = packageName == null ? sourceFileName : packageName + "/" + sourceFileName;
        String summary = "Do phu %s: %d/%d dong duoc phu (%d dong phu mot phan), %d dong khong phu"
                .formatted(sourceFileName, executed, lines.size(), partial, missed);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tool", "jacoco");
        content.put("package", packageName);
        content.put("source_file", sourceFileName);
        content.put("lines_total", lines.size());
        content.put("lines_covered", executed);
        content.put("lines_partial", partial);
        content.put("lines_missed", missed);
        content.put("methods_total", methods.size());
        content.put("classes", classes.stream().map(clazz -> XmlParsers.attr(clazz, "name")).toList());
        // KHONG dua 171 muc per-line vao day: do la viec cua bang coverage_line, nhet vao JSONB
        // nua la tao hai nguon su that cho cung mot con so. Cung khong dua <sessioninfo> (chua
        // ten may cua nguoi chay).

        return new CoverageDraft(
                EvidenceType.COVERAGE_INFORMATION,
                EvidenceSource.jacoco,
                rawFilePath,
                null,
                null,
                // Chi dat symbol khi sourcefile anh xa dung MOT class (20/20 truong hop o vulnshop);
                // nhieu class trong mot file thi khong co symbol nao dung.
                classes.size() == 1 ? XmlParsers.attr(classes.get(0), "name").replace('/', '.') : null,
                summary,
                content,
                packageName,
                sourceFileName,
                lines,
                methods);
    }

    private int counter(Element parent, String type, String attribute) {
        for (Element counter : XmlParsers.directChildren(parent, "counter")) {
            if (type.equals(XmlParsers.attr(counter, "type"))) {
                return orZero(XmlParsers.intAttr(counter, attribute));
            }
        }
        return 0;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
