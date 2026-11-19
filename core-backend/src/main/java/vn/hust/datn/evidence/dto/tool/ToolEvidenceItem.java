package vn.hust.datn.evidence.dto.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Item dùng chung cho {@code get_code_context}/{@code get_related_tests}/{@code get_coverage}.
 *
 * <p>{@code method} và {@code weight} BẮT BUỘC có mặt: V2 migration ghi rõ STACK_FRAME đáng tin
 * nhất, NAMING_CONVENTION yếu nhất; evidence-model §6 phát hiện 2 chứng minh 3/5 test hỏng không
 * có stack frame production. Thiếu hai trường này, LLM không phân biệt được bằng chứng mạnh/yếu,
 * và tuần 15 không đo được "bao nhiêu % link chỉ dựa vào quy ước tên".
 *
 * <p>{@code snippet} là MÃ NGUỒN của bằng chứng {@code CODE_CONTEXT} (thân method/lớp chứa dòng bị cảnh
 * báo). Thiếu nó thì {@code get_code_context} chỉ trả về một dòng tóm tắt "dòng 38-45" và agent không
 * bao giờ đọc được mã — lỗi này từng khiến agent kết luận trên mã nó chưa hề thấy. Chỉ có ở item
 * CODE_CONTEXT; các loại khác để null và KHÔNG xuất hiện trong JSON (không tốn token vô ích).
 *
 * <p>snake_case vì đây là payload tool — LLM sinh/đọc theo quy ước đó (ADR 0005), forward nguyên
 * văn không dịch tên trường.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolEvidenceItem(
        String shortId,
        EvidenceType type,
        String summary,
        String filePath,
        Integer startLine,
        String symbol,
        EvidenceLinkRelation relation,
        EvidenceLinkMethod method,
        double weight,
        @JsonInclude(JsonInclude.Include.NON_NULL) String snippet) {}
