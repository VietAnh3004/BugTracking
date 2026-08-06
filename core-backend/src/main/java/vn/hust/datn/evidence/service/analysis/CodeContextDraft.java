package vn.hust.datn.evidence.service.analysis;

import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Đoạn mã nguồn BAO QUANH một vị trí cảnh báo — method, constructor, khối khởi tạo hoặc class chứa
 * nó. Sinh bởi {@link CodeContextExtractor} trên AST của JavaParser.
 *
 * <p>Đây là loại evidence duy nhất KHÔNG đến từ artifact analyzer mà từ chính mã nguồn đã upload.
 * Nó tồn tại vì hai lý do độc lập:
 *
 * <ul>
 *   <li>JaCoCo không trả lời được "dòng 42 thuộc method nào" (cấu trúc kép, method chỉ có dòng đầu
 *       và không theo thứ tự dòng — {@code docs/evidence-model.md} phát hiện 3). Nguồn sự thật cho
 *       quan hệ dòng ↔ method phải là một parser Java thật.
 *   <li>LLM cần đọc được đoạn mã bị cảnh báo. Không có nó, agent chỉ có {@code file:line} và một
 *       câu tóm tắt của tool — không đủ để phán đoán true/false positive.
 * </ul>
 *
 * <h2>Vì sao sinh ở đường ingest chứ không ở EvidenceLinker</h2>
 *
 * {@code EvidenceLinker} chạy lại được, còn cái này thì KHÔNG được phép: {@code short_id} sinh từ
 * {@code seq_in_run}, nên mỗi lần sinh/xoá lại evidence là mọi {@code claim_citation} đã ghi trỏ
 * vào hư không — trong khi {@code resolution} của chúng vẫn ghi {@code RESOLVED}. Đó là hỏng im
 * lặng phá thẳng chỉ số Evidence Citation Accuracy.
 *
 * <p>Đánh đổi phải nói thẳng: sửa heuristic của {@link CodeContextExtractor} thì phải phân tích
 * lại từ đầu, không rebuild được. Ranh giới là: linker tính lại được **cách khớp** dòng ↔ context,
 * nhưng không tính lại được **kết quả parse**.
 *
 * @param kind {@code METHOD} / {@code CONSTRUCTOR} / {@code STATIC_INITIALIZER} / {@code FIELD} /
 *     {@code TYPE} / {@code WINDOW}. Nằm trong {@code content} chứ không lên cột riêng — cùng tiền
 *     lệ {@code PathMatchKind}: thêm cột nghĩa là thêm CHECK và thêm một method vào
 *     {@code EnumCheckConstraintTest} cho thứ chưa có consumer nào ở tầng SQL.
 * @param jacocoMethodName tên method THEO CÁCH JACOCO GỌI ({@code <init>} cho constructor,
 *     {@code <clinit>} cho khối static). Tính sẵn ở đây để EvidenceLinker tầng 2 khớp thẳng với
 *     {@code coverage_method.method_name}, thay vì mỗi lần link lại suy lại một lần nữa.
 */
public record CodeContextDraft(
        EvidenceType type,
        EvidenceSource source,
        String rawFilePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        Map<String, Object> content,
        String kind,
        String jacocoMethodName)
        implements EvidenceDraft {}
