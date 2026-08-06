package vn.hust.datn.evidence.service.linker;

import java.util.UUID;

import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;

/**
 * Một liên kết ĐỀ XUẤT, trước khi dedup và trước khi chạm DB.
 *
 * <p>Tách khỏi entity {@code EvidenceLink} để ba tầng của linker chạy được thuần hàm trên
 * {@link LinkerInput} — cùng ranh giới mà {@code EvidenceDraft} dựng cho parser.
 *
 * @param weight độ tin cậy của LIÊN KẾT, không phải mức nghiêm trọng của cảnh báo và cũng không
 *     phải tỉ lệ độ phủ. Nhầm chỗ này thì mọi xếp hạng bằng chứng ở tuần 12-13 sai
 * @param rationale câu giải thích DUY NHẤT mà người dùng đọc được cho một liên kết — luôn phải
 *     dựng được, kể cả khi ngắn
 */
public record LinkCandidate(
        UUID fromEvidenceId,
        UUID toEvidenceId,
        EvidenceLinkRelation relation,
        EvidenceLinkMethod method,
        double weight,
        String rationale) {

    /** Khoá dedup, khớp đúng {@code UNIQUE (from_evidence_id, to_evidence_id, relation)} của DB. */
    public String dedupeKey() {
        return fromEvidenceId + "|" + toEvidenceId + "|" + relation;
    }
}
