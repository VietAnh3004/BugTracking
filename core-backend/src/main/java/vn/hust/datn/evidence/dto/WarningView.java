package vn.hust.datn.evidence.dto;

import java.util.UUID;

import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Read model phẳng cho {@code warning} — sinh bằng JPQL constructor projection, KHÔNG bằng
 * MapStruct từ entity {@code Warning}.
 *
 * <p>Lý do: {@code Warning.evidenceRecord} là {@code @OneToOne LAZY} một chiều; map từ entity sẽ
 * hoặc ném {@code LazyInitializationException} ngoài transaction, hoặc gây N+1 query mỗi item
 * trong list. Constructor projection là một query duy nhất, không lazy loading — xem
 * {@code WarningRepository.findFlatByRunAndShortId}.
 *
 * <p>{@code cweIds} cố tình KHÔNG có ở đây: kiểu {@code Integer[]} không đưa vào JPQL constructor
 * expression sạch được. Endpoint detail cần nó thì query riêng.
 */
public record WarningView(
        UUID evidenceId,
        String shortId,
        UUID analysisRunId,
        String filePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        WarningTool tool,
        String ruleId,
        String ruleIdNormalized,
        String severityRaw,
        WarningSeverity severityNormalized,
        boolean hasPrimaryLocation) {}
