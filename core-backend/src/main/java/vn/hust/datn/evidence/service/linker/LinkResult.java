package vn.hust.datn.evidence.service.linker;

import java.util.Map;

/**
 * Số đo của một lần liên kết.
 *
 * <h2>Vì sao trả LÊN thay vì tự ghi xuống {@code analysis_run}</h2>
 *
 * Ở đường ingest, {@code AnalysisRunPersistenceService.finalizeRun} mang
 * {@code @Transactional(REQUIRES_NEW)}: nó TREO transaction hiện tại rồi đọc lại hàng
 * {@code analysis_run} trong một transaction mới. Một {@code UPDATE} chưa commit từ bên ngoài là
 * vô hình với nó, và dirty-check của Hibernate sẽ ghi đè cả hàng bằng giá trị cũ — bộ đếm về 0
 * trong im lặng, làm vô hiệu mọi ràng buộc "0 liên kết = lỗi" trong khi nhìn vẫn như đang chạy.
 *
 * <p>Cùng họ bài học với {@code @Transactional(noRollbackFor = ...)} của {@code RefreshTokenService}:
 * ghi dữ liệu quan trọng qua một ranh giới transaction luôn phải xét lại.
 *
 * @param unlinkedWarningCount số cảnh báo không nhận được liên kết nào. Tách khỏi
 *     {@code linkCount} vì 30/33 cảnh báo trơ trọi là tín hiệu khác hẳn 0/33
 * @param countByMethod phân bố theo {@code EvidenceLinkMethod} — nguồn số liệu "bao nhiêu % liên
 *     kết chỉ dựa vào quy ước đặt tên" của tuần 15
 */
public record LinkResult(int linkCount, int unlinkedWarningCount, int deletedCount, Map<String, Object> countByMethod) {

    public static LinkResult empty() {
        return new LinkResult(0, 0, 0, Map.of());
    }
}
