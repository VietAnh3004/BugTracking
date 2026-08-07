package vn.hust.datn.evidence.service.citation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ClaimVerdict;

/**
 * Kết quả hậu kiểm: đúng câu trả lời của LLM, nhưng mọi verdict đã được đối chiếu với Evidence
 * Store thật.
 *
 * <p>Không nhận định nào bị xoá dù trích dẫn hỏng tới đâu — chúng là mẫu số của Unsupported Claim
 * Rate, bỏ đi là mất chính con số mà đồ án cần đo (kế hoạch §4).
 *
 * @param verdict kết luận sau hậu kiểm
 * @param downgradedFrom verdict gốc, hoặc {@code null} nếu không bị hạ cấp
 * @param summary giữ nguyên văn của LLM
 * @param claims mọi nhận định, kèm kết quả tra từng trích dẫn
 * @param contradictingShortIds giữ nguyên văn; không thuộc mẫu số của chỉ số nào
 */
public record ValidatedAnswer(
        ClaimVerdict verdict,
        ClaimVerdict downgradedFrom,
        String summary,
        List<ValidatedClaim> claims,
        List<String> contradictingShortIds) {

    /**
     * Một nhận định sau hậu kiểm.
     *
     * @param downgradedFrom khác {@code null} nghĩa là nhận định này không có trích dẫn nào tra
     *     được về bằng chứng thật trong run đang xét
     */
    public record ValidatedClaim(
            String text,
            ClaimVerdict verdict,
            ClaimVerdict downgradedFrom,
            BigDecimal confidence,
            List<ResolvedCitation> citations) {

        /** {@code true} khi có ít nhất một trích dẫn trỏ về bằng chứng thật trong đúng run. */
        public boolean supported() {
            return citations.stream().anyMatch(ResolvedCitation::isResolved);
        }
    }

    /**
     * Một lượt trích dẫn đã tra xong.
     *
     * @param citedShortIdRaw NGUYÊN VĂN chuỗi LLM viết, kể cả khi vô nghĩa
     * @param evidenceRecordId khác {@code null} chỉ khi {@code resolution} là {@code RESOLVED}
     */
    public record ResolvedCitation(String citedShortIdRaw, ClaimCitationResolution resolution, UUID evidenceRecordId) {

        public boolean isResolved() {
            return resolution == ClaimCitationResolution.RESOLVED;
        }
    }

    /** Số nhận định không có trích dẫn hợp lệ nào — tử số của Unsupported Claim Rate. */
    public long unsupportedClaimCount() {
        return claims.stream().filter(c -> !c.supported()).count();
    }

    /** Tổng số lượt trích dẫn, tính cả những lượt hỏng — mẫu số của Evidence Citation Accuracy. */
    public long citationCount() {
        return claims.stream().mapToLong(c -> c.citations().size()).sum();
    }

    /** Số lượt trích dẫn tra được về bằng chứng thật trong đúng run. */
    public long resolvedCitationCount() {
        return claims.stream()
                .flatMap(c -> c.citations().stream())
                .filter(ResolvedCitation::isResolved)
                .count();
    }
}
