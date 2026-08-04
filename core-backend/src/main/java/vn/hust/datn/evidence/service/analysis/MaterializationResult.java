package vn.hust.datn.evidence.service.analysis;

import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Số đo của một lần materialize — đầu vào để {@code AnalysisRunService} quyết định trạng thái run
 * và để ghi các bộ đếm của {@code analysis_run}.
 *
 * @param evidenceCount            tổng số {@code evidence_record} đã ghi
 * @param findingCount             CHỈ đếm {@link EvidenceType#STATIC_WARNING}. Đếm cả 90 evidence
 *     thì {@code ck_run_not_silently_empty} vẫn xanh ngay cả khi CẢ HAI analyzer tĩnh trả rỗng mà
 *     test vẫn chạy — đúng loại hỏng im lặng mà ràng buộc đó sinh ra để chặn
 * @param locationCount            số vị trí đã THỬ đối chiếu với {@code snapshot_file.path}. Frame
 *     JDK/JUnit/thư viện không tính (xem {@link PathMatchKind#NOT_APPLICABLE})
 * @param unresolvedLocationCount  số vị trí không đối chiếu được; mẫu số là {@code locationCount}
 * @param countByType              phân bố theo loại, dùng cho log tổng kết mỗi run
 */
public record MaterializationResult(
        int evidenceCount,
        int findingCount,
        int locationCount,
        int unresolvedLocationCount,
        Map<EvidenceType, Integer> countByType) {

    /**
     * Mọi vị trí đều hỏng. {@code ck_run_paths_not_all_unresolved} sẽ từ chối một run
     * {@code SUCCEEDED} ở trạng thái này — kiểm ở đây để báo lỗi có nghĩa thay vì để ràng buộc DB
     * nổ ra lúc flush.
     */
    public boolean allLocationsUnresolved() {
        return locationCount > 0 && unresolvedLocationCount == locationCount;
    }
}
