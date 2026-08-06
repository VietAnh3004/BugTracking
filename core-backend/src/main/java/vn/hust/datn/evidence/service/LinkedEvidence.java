package vn.hust.datn.evidence.service;

import vn.hust.datn.evidence.domain.EvidenceLink;
import vn.hust.datn.evidence.domain.EvidenceRecord;

/**
 * Một bằng chứng liên quan, kèm CHÍNH liên kết dẫn tới nó.
 *
 * <p>Tồn tại để đường tool và đường UI dùng chung đúng một truy vấn (ADR 0005 quyết định 3: khác
 * DTO đầu ra, không khác SQL). Trước đây {@code relatedEvidence} map thẳng sang
 * {@code ToolEvidenceItem} nên đường UI muốn thêm một trường — {@code rationale} chẳng hạn — là
 * phải viết lại truy vấn lần hai, và hai truy vấn song song sớm muộn cũng lệch.
 *
 * <p>Đơn vị là MỘT LIÊN KẾT, không phải một evidence: {@code UNIQUE (from, to, relation)} cho phép
 * cùng một cặp mang nhiều quan hệ.
 *
 * @param outgoing {@code true} khi neo nằm ở đầu {@code from}. Hướng có nghĩa với người đọc — "cảnh
 *     báo này nằm trong đoạn mã kia" khác "test kia ủng hộ cảnh báo này" — nên không được nuốt.
 */
public record LinkedEvidence(EvidenceLink link, EvidenceRecord record, boolean outgoing) {}
