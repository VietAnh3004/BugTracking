package vn.hust.datn.evidence.service.linker;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceLinkMethod;

/**
 * Gom ứng viên liên kết và giữ lại bản MẠNH NHẤT cho mỗi cặp {@code (from, to, relation)}.
 *
 * <h2>Vì sao không để {@code ON CONFLICT DO NOTHING} lo</h2>
 *
 * {@code UNIQUE (from_evidence_id, to_evidence_id, relation)} sẽ vỡ khi hai tầng cùng đề xuất một
 * cặp — chuyện xảy ra thật: một cảnh báo có nhiều vị trí trong CÙNG một method sinh ra cùng một
 * liên kết {@code LOCATED_IN} nhiều lần.
 *
 * <p>Chữa bằng {@code ON CONFLICT DO NOTHING} thì bản nào chèn trước thắng — tức thứ tự chèn quyết
 * định độ tin cậy được ghi lại. Một liên kết {@code STACK_FRAME} (bằng chứng trực tiếp, weight 1.0)
 * có thể bị {@code NAMING_CONVENTION} (weight 0.3) chiếm chỗ, và tỉ lệ "liên kết yếu" mà tuần 15
 * báo cáo sai theo hướng bi quan mà không ai phát hiện.
 *
 * <p>Nên: dedup Ở ĐÂY theo quy tắc tường minh, và INSERT không có {@code ON CONFLICT} — trùng sau
 * dedup nghĩa là lớp này có bug, và bug đó phải nổ chứ không được nuốt.
 */
public class LinkAccumulator {

    /**
     * Thứ bậc khi hai ứng viên cùng {@code weight}. Bằng chứng TRỰC TIẾP (khớp đúng dòng, khớp
     * frame thật) đứng trên bằng chứng SUY RA (tổng hợp method, quy ước tên).
     */
    private static final List<EvidenceLinkMethod> PRECEDENCE = List.of(
            EvidenceLinkMethod.STACK_FRAME,
            EvidenceLinkMethod.LINE_EXACT,
            EvidenceLinkMethod.RANGE_OVERLAP,
            EvidenceLinkMethod.SYMBOL_MATCH,
            EvidenceLinkMethod.CROSS_TOOL_AGREEMENT,
            EvidenceLinkMethod.METHOD_AGGREGATE,
            EvidenceLinkMethod.LINE_NEAREST_EXECUTABLE,
            EvidenceLinkMethod.NAMING_CONVENTION);

    private static final Comparator<LinkCandidate> STRONGEST_FIRST = Comparator.comparingDouble(
                    LinkCandidate::weight)
            .reversed()
            .thenComparingInt(candidate -> PRECEDENCE.indexOf(candidate.method()));

    private final Map<String, LinkCandidate> byKey = new LinkedHashMap<>();

    public void add(LinkCandidate candidate) {
        byKey.merge(
                candidate.dedupeKey(),
                candidate,
                (existing, incoming) -> STRONGEST_FIRST.compare(existing, incoming) <= 0 ? existing : incoming);
    }

    public void addAll(Collection<LinkCandidate> candidates) {
        candidates.forEach(this::add);
    }

    /** Thứ tự chèn ổn định theo thứ tự thêm vào — để hai lần chạy cho ra cùng một tập hàng. */
    public List<LinkCandidate> candidates() {
        return List.copyOf(byKey.values());
    }

    /** Mọi bậc của {@link EvidenceLinkMethod} phải có mặt trong {@link #PRECEDENCE}. */
    static List<EvidenceLinkMethod> precedence() {
        return PRECEDENCE;
    }
}
