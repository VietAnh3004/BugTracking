package vn.hust.datn.evidence.service.analysis;

import java.util.List;
import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Độ phủ của MỘT file nguồn — một evidence cho cả file, không phải một evidence cho mỗi dòng.
 *
 * <p>171 dòng của vulnshop thành 171 "đơn vị trích dẫn" là vô nghĩa với LLM (không ai trích dẫn
 * "dòng 27 có ci=0"), và ở repo gấp 100 lần thì {@code evidence_record} phồng lên 17.000 hàng mỗi
 * run. Chi tiết từng dòng nằm ở bảng {@code coverage_line}.
 */
public record CoverageDraft(
        EvidenceType type,
        EvidenceSource source,
        String rawFilePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String summary,
        Map<String, Object> content,
        String packageName,
        String sourceFileName,
        List<CoverageLineDraft> lines,
        List<CoverageMethodDraft> methods)
        implements EvidenceDraft {}
