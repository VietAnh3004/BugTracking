package vn.hust.datn.evidence.service.analysis;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.WarningView;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;
import vn.hust.datn.evidence.repository.WarningRepository;
import vn.hust.datn.evidence.service.EvidenceQueryService;
import vn.hust.datn.evidence.service.SnapshotQueryService;

/**
 * Đường ĐỌC của UI cho analysis run — tách khỏi {@link AnalysisRunService} (đường ghi) vì hai bên
 * có mô hình transaction ngược nhau: bên kia cố tình không {@code @Transactional}, bên này
 * {@code readOnly = true} cho mọi thứ.
 *
 * <p>Phân quyền đi qua {@code EvidenceQueryService.requireRunOwnedBy} — method viết từ tuần 5-7 và
 * tới giờ chưa từng có caller; đây là caller đầu tiên, đúng như Javadoc của nó dự liệu. Không
 * thuộc về bạn ⇒ 404, KHÔNG phải 403: 403 tiết lộ rằng tài nguyên có tồn tại.
 */
@Service
@Transactional(readOnly = true)
public class AnalysisRunQueryService {

    private final AnalysisRunRepository analysisRunRepository;
    private final WarningRepository warningRepository;
    private final EvidenceQueryService evidenceQueryService;
    private final SnapshotQueryService snapshotQueryService;

    public AnalysisRunQueryService(
            AnalysisRunRepository analysisRunRepository,
            WarningRepository warningRepository,
            EvidenceQueryService evidenceQueryService,
            SnapshotQueryService snapshotQueryService) {
        this.analysisRunRepository = analysisRunRepository;
        this.warningRepository = warningRepository;
        this.evidenceQueryService = evidenceQueryService;
        this.snapshotQueryService = snapshotQueryService;
    }

    /** Không đòi snapshot {@code READY}: một snapshot FAILED vẫn có thể có run cũ đáng xem. */
    public Page<AnalysisRun> listBySnapshot(UUID snapshotId, UUID ownerId, Pageable pageable) {
        snapshotQueryService.get(snapshotId, ownerId);
        // Repository trả List (khớp idx_analysis_run_snapshot) — cắt trang phía ứng dụng là chấp
        // nhận được vì số run trên một snapshot là hàng chục, không phải hàng nghìn.
        var runs = analysisRunRepository.findBySnapshotIdOrderByStartedAtDesc(snapshotId);
        int from = Math.min((int) pageable.getOffset(), runs.size());
        int to = Math.min(from + pageable.getPageSize(), runs.size());
        return new org.springframework.data.domain.PageImpl<>(runs.subList(from, to), pageable, runs.size());
    }

    /**
     * CỐ Ý không gọi {@code ensureRunQueryable}: đây chính là endpoint để FE đọc {@code status} và
     * {@code errorMessage} của một run {@code FAILED} hoặc còn {@code RUNNING}. Chặn ở đây thì
     * người dùng thấy một màn hình trống không giải thích được gì.
     */
    public AnalysisRun get(UUID runId, UUID ownerId) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        return analysisRunRepository.findById(runId).orElseThrow(() -> new ResourceNotFoundException("analysis_run", runId));
    }

    /**
     * Danh sách cảnh báo. CÓ gọi {@code ensureRunQueryable} — ngược với {@link #get}: "rỗng vì run
     * chưa xong" và "rỗng vì thật sự không có cảnh báo nào" phải phân biệt được, nếu không cả hai
     * đều hiện ra là một danh sách trống.
     */
    public Page<WarningView> listWarnings(
            UUID runId, UUID ownerId, WarningTool tool, WarningSeverity severity, String filePath, Pageable pageable) {
        evidenceQueryService.requireRunOwnedBy(runId, ownerId);
        evidenceQueryService.ensureRunQueryable(runId);

        // Bỏ Sort của client: thứ tự đã cố định theo severity_rank trong chính câu truy vấn. Cho
        // phép client sort sẽ mở lại đúng cái bẫy mà cột generated đó sinh ra để chặn — ?sort=
        // severityNormalized cho ra HIGH, INFO, LOW, MEDIUM (thứ tự chữ cái) và trông vẫn hợp lý.
        Pageable khongSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return warningRepository.findForUi(runId, tool, severity, blankToNull(filePath), khongSort);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
