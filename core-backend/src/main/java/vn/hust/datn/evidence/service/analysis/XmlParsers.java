package vn.hust.datn.evidence.service.analysis;

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Hai cấu hình đọc XML an toàn, và các tiện ích duyệt DOM cho parser artifact.
 *
 * <h2>Vì sao phải có HAI factory chứ không một</h2>
 *
 * Cách cứng hoá chuẩn chống XXE là {@code disallow-doctype-decl=true}. Nhưng {@code jacoco.xml}
 * THẬT có khai báo DOCTYPE:
 *
 * <pre>{@code <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">}</pre>
 *
 * nên bật cờ đó sẽ <b>từ chối chính file cần đọc</b>. Với JaCoCo phải dùng bộ nới hơn: cho phép
 * DOCTYPE tồn tại nhưng cấm tuyệt đối việc đi nạp DTD/entity ngoài — vừa chống XXE, vừa không vỡ
 * khi {@code report.dtd} không tồn tại cạnh file (nó không bao giờ tồn tại).
 *
 * <p>SpotBugs XML và Surefire XML không có DOCTYPE nên dùng bộ nghiêm ngặt.
 */
public final class XmlParsers {

    private XmlParsers() {}

    /** Cho SpotBugs/Surefire: cấm luôn cả việc khai báo DOCTYPE. */
    public static Document parseStrictNoDoctype(byte[] xml) {
        return parse(xml, true);
    }

    /** Cho JaCoCo: chấp nhận DOCTYPE nhưng không bao giờ đi nạp DTD/entity ngoài. */
    public static Document parseAllowLocalDoctype(byte[] xml) {
        return parse(xml, false);
    }

    private static Document parse(byte[] xml, boolean disallowDoctype) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            if (disallowDoctype) {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            }
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // Không có EntityResolver mặc định nào đi ra mạng vì mọi cờ external đã tắt; đặt
            // resolver trả rỗng để chắc chắn ngay cả khi một implementation khác bỏ qua cờ.
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Khong cau hinh duoc trinh doc XML an toan", e);
        } catch (Exception e) {
            throw new InvalidArtifactException("Khong doc duoc XML: " + e.getMessage(), e);
        }
    }

    /**
     * Con TRỰC TIẾP theo tên thẻ.
     *
     * <p><b>Bẫy số một của cả đợt parser</b>: {@code Element.getElementsByTagName} trong Java là
     * ĐỆ QUY (tương đương {@code .//} của XPath), không phải {@code findall} của Python. Trong
     * SpotBugs XML, {@code <SourceLine>} xuất hiện ở BA tầng lồng nhau — bên trong {@code <Class>},
     * bên trong {@code <Method>}, và là con trực tiếp của {@code <BugInstance>} — mà chỉ con trực
     * tiếp mới là vị trí của cảnh báo. Dùng {@code getElementsByTagName} sẽ ra 90 vị trí thay vì
     * 33, và không có gì báo lỗi.
     */
    public static java.util.List<Element> directChildren(Element parent, String tagName) {
        java.util.List<Element> result = new java.util.ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /** Con trực tiếp đầu tiên theo tên thẻ, hoặc null. */
    public static Element firstDirectChild(Element parent, String tagName) {
        java.util.List<Element> children = directChildren(parent, tagName);
        return children.isEmpty() ? null : children.get(0);
    }

    /** Thuộc tính, trả null (không phải chuỗi rỗng) khi vắng mặt. */
    public static String attr(Element element, String name) {
        if (element == null || !element.hasAttribute(name)) {
            return null;
        }
        String value = element.getAttribute(name);
        return value.isEmpty() ? null : value;
    }

    /** Thuộc tính dạng số nguyên, null khi vắng mặt hoặc không phải số. */
    public static Integer intAttr(Element element, String name) {
        String raw = attr(element, name);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Nội dung text của con trực tiếp đầu tiên theo tên thẻ, đã trim; null nếu không có. */
    public static String childText(Element parent, String tagName) {
        Element child = firstDirectChild(parent, tagName);
        if (child == null || child.getTextContent() == null) {
            return null;
        }
        String text = child.getTextContent().trim();
        return text.isEmpty() ? null : text;
    }
}
