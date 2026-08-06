package vn.hust.datn.evidence.dto;

import java.util.List;
import java.util.UUID;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tất cả những gì màn hình Evidence Explorer cần cho MỘT bằng chứng, trong MỘT lượt gọi.
 *
 * <h2>Vì sao một endpoint gộp chứ không phải bốn endpoint nhỏ</h2>
 *
 * <ul>
 *   <li>Màn hình luôn cần cả bốn nhóm cùng lúc. FE và BE khác domain (ADR 0002) nên mỗi request
 *       mang {@code Authorization} kéo theo một preflight {@code OPTIONS} — bốn endpoint là tám
 *       lượt đi-về cho một màn hình.
 *   <li>Backend chỉ quét {@code evidence_link} MỘT lần cho cả bốn nhóm. Tách ra là quét bốn lần
 *       cùng một tập link của cùng một neo.
 *   <li><b>"Rỗng" phải là một sự kiện nguyên tử.</b> Bốn panel tải độc lập sẽ có khoảnh khắc "test
 *       rỗng nhưng coverage đang tải" — người dùng không phân biệt được với "thật sự không có
 *       test". Mà phân biệt được hai thứ đó chính là điều kiện để verdict
 *       {@code INSUFFICIENT_EVIDENCE} có nghĩa.
 * </ul>
 *
 * <p>KHÔNG phân trang: số liên kết của một cảnh báo là hàng đơn vị tới hàng chục.
 *
 * <p>KHÔNG chứa nội dung mã nguồn: thứ đó lấy qua
 * {@code GET /snapshots/{id}/file?path=&fromLine=&toLine=} đã có từ tuần 5-7. Nhân đôi taxonomy
 * lỗi {@code binary-file}/{@code file-content-not-stored} sang đây là tạo chỗ thứ hai để sai.
 */
public record EvidenceDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evidenceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shortId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID analysisRunId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Để FE dựng link tới mã nguồn mà không phải hỏi thêm")
                UUID snapshotId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String filePath,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer startLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer endLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String symbol,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Chỉ có khi type là STATIC_WARNING")
                WarningDetailView warning,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Quan hệ LOCATED_IN — đoạn mã bao quanh")
                List<RelatedEvidenceView> codeContext,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "SUPPORTS/CONTRADICTS từ test")
                List<RelatedEvidenceView> tests,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Quan hệ COVERS")
                List<RelatedEvidenceView> coverage,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Liên kết không thuộc ba nhóm trên, vd CROSS_TOOL_AGREEMENT giữa hai cảnh báo")
                List<RelatedEvidenceView> other,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Tổng số liên kết. 0 là một KẾT QUẢ hợp lệ, không phải lỗi")
                int relatedTotal) {}
