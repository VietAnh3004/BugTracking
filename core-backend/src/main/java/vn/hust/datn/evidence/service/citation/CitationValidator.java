package vn.hust.datn.evidence.service.citation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ClaimVerdict;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;

import lombok.RequiredArgsConstructor;

/**
 * Đối chiếu từng Evidence ID mà LLM trích dẫn với Evidence Store thật, rồi hạ cấp verdict của
 * nhận định nào không có căn cứ.
 *
 * <p><b>Vì sao lớp này ở core-backend chứ không ở agent-service:</b> đề cương mục 3.4.2 đòi hậu
 * kiểm "độc lập với LLM". Đặt nó trong cùng process với agent thì tính độc lập chỉ là quy ước nội
 * bộ — ai sửa agent loop cũng sửa được luôn bước kiểm. Đặt ngoài process, qua một ranh giới mạng,
 * biến nó thành ràng buộc kiến trúc: agent-service không có kết nối DB nên nó KHÔNG THỂ tự tuyên
 * bố một trích dẫn là hợp lệ (ADR 0001, ADR 0010).
 *
 * <p><b>Bốn kết cục của một lượt trích dẫn</b>, cố ý tách bạch vì mỗi cái nói về một lỗi khác của
 * mô hình:
 *
 * <ul>
 *   <li>{@code RESOLVED} — có thật, trong đúng run đang xét.
 *   <li>{@code WRONG_RUN} — có thật nhưng thuộc run khác. Đây là lỗi <em>ngữ cảnh</em>: mô hình
 *       nhớ ID từ một lượt trước, hoặc từ chính prompt của hội thoại khác.
 *   <li>{@code UNKNOWN_ID} — đúng khuôn dạng nhưng không tồn tại ở đâu cả. Đây là <em>bịa</em>.
 *   <li>{@code MALFORMED} — không phải Evidence ID. Thường là mô hình viết mô tả thay vì ID.
 * </ul>
 *
 * <p><b>Không xoá nhận định nào</b>, kể cả khi mọi trích dẫn đều hỏng — chúng là mẫu số của
 * Unsupported Claim Rate. Xoá đi thì chỉ số này luôn đẹp và luôn vô nghĩa.
 */
@Service
@RequiredArgsConstructor
public class CitationValidator {

    private final EvidenceRecordRepository evidenceRecordRepository;

    /**
     * Hậu kiểm một câu trả lời trong phạm vi đúng một analysis run.
     *
     * @param analysisRunId phạm vi mà {@code short_id} có nghĩa; ngoài phạm vi này một Evidence ID
     *     là chuỗi mơ hồ, nên tham số này bắt buộc chứ không phải tuỳ chọn
     */
    @Transactional(readOnly = true)
    public ValidatedAnswer validate(UUID analysisRunId, AnswerDraft draft) {
        List<ValidatedAnswer.ValidatedClaim> claims = new ArrayList<>(draft.claims().size());
        for (AnswerDraft.ClaimDraft claim : draft.claims()) {
            claims.add(validateClaim(analysisRunId, claim, draft.verdict()));
        }

        boolean anySupported = claims.stream().anyMatch(ValidatedAnswer.ValidatedClaim::supported);
        ClaimVerdict verdict = anySupported ? draft.verdict() : ClaimVerdict.INSUFFICIENT_EVIDENCE;

        return new ValidatedAnswer(
                verdict,
                downgradedFrom(draft.verdict(), verdict),
                draft.summary(),
                List.copyOf(claims),
                List.copyOf(draft.contradictingShortIds()));
    }

    private ValidatedAnswer.ValidatedClaim validateClaim(
            UUID analysisRunId, AnswerDraft.ClaimDraft claim, ClaimVerdict answerVerdict) {

        List<ValidatedAnswer.ResolvedCitation> citations = claim.citedShortIds().stream()
                .map(raw -> resolve(analysisRunId, raw))
                .toList();

        boolean supported = citations.stream().anyMatch(ValidatedAnswer.ResolvedCitation::isResolved);
        ClaimVerdict verdict = supported ? answerVerdict : ClaimVerdict.INSUFFICIENT_EVIDENCE;

        return new ValidatedAnswer.ValidatedClaim(
                claim.text(), verdict, downgradedFrom(answerVerdict, verdict), claim.confidence(), citations);
    }

    private ValidatedAnswer.ResolvedCitation resolve(UUID analysisRunId, String raw) {
        Optional<String> canonical = ShortIdFormat.canonical(raw);
        if (canonical.isEmpty()) {
            return new ValidatedAnswer.ResolvedCitation(raw, ClaimCitationResolution.MALFORMED, null);
        }

        String shortId = canonical.get();
        Optional<EvidenceRecord> inRun = evidenceRecordRepository.findByAnalysisRunIdAndShortId(analysisRunId, shortId);
        if (inRun.isPresent()) {
            return new ValidatedAnswer.ResolvedCitation(raw, ClaimCitationResolution.RESOLVED, inRun.get().getId());
        }

        ClaimCitationResolution resolution = evidenceRecordRepository.existsByShortId(shortId)
                ? ClaimCitationResolution.WRONG_RUN
                : ClaimCitationResolution.UNKNOWN_ID;
        return new ValidatedAnswer.ResolvedCitation(raw, resolution, null);
    }

    /**
     * Verdict gốc để ghi lại, hoặc {@code null} khi không có gì bị hạ cấp.
     *
     * <p>Trả {@code null} khi LLM vốn đã kết luận {@code INSUFFICIENT_EVIDENCE}: ghi
     * "hạ từ INSUFFICIENT_EVIDENCE xuống INSUFFICIENT_EVIDENCE" sẽ thổi phồng số lần hạ cấp trong
     * báo cáo tuần 15 bằng những lần chẳng có gì xảy ra.
     */
    private ClaimVerdict downgradedFrom(ClaimVerdict original, ClaimVerdict effective) {
        return original == effective ? null : original;
    }
}
