package vn.hust.datn.evidence.dto.tool;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Shape trả về dùng chung cho {@code get_code_context}/{@code get_related_tests}/{@code
 * get_coverage} — không có {@code page}, chỉ {@code total}: LLM không phân trang được, chỉ cần
 * biết mình đang nhìn phần bị cắt hay không.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ItemsResult(List<ToolEvidenceItem> items, int total) {

    public static ItemsResult of(List<ToolEvidenceItem> items) {
        return new ItemsResult(items, items.size());
    }
}
