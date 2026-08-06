package vn.hust.datn.evidence.service.analysis;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.ArtifactSource;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;

/**
 * Hai thao tác DB của một lần phân tích, MỖI THAO TÁC MỘT TRANSACTION RIÊNG — cùng khuôn và cùng
 * lý do với {@code SnapshotPersistenceService}.
 *
 * <p>Tách class là BẮT BUỘC chứ không phải để gọn: nếu hai method này nằm chung với
 * {@code AnalysisRunService.ingest()} và được gọi bằng {@code this.finalizeRun(...)}, lời gọi đó
 * là self-invocation — KHÔNG đi qua Spring AOP proxy — nên {@code REQUIRES_NEW} bị bỏ qua hoàn
 * toàn: không lỗi, không cảnh báo, chỉ lặng lẽ chạy trong transaction hiện có hoặc không transaction
 * nào. Hậu quả cụ thể ở đây: nhánh ghi FAILED sẽ nằm trong chính transaction vừa hỏng và bị
 * rollback cùng nó, để lại một run treo mãi ở {@code RUNNING}.
 */
@Service
public class AnalysisRunPersistenceService {

    private final AnalysisRunRepository analysisRunRepository;

    public AnalysisRunPersistenceService(AnalysisRunRepository analysisRunRepository) {
        this.analysisRunRepository = analysisRunRepository;
    }

    /**
     * Bia mộ: hàng {@code RUNNING} phải COMMIT NGAY, độc lập với việc parse/ghi phía sau có thành
     * công hay không — nếu không, một crash giữa chừng không để lại dấu vết gì trong DB và người
     * vận hành không có gì để tra.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisRun createRunning(UUID snapshotId) {
        return createRunning(snapshotId, ArtifactSource.MANUAL_UPLOAD);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisRun createRunning(UUID snapshotId, ArtifactSource artifactSource) {
        AnalysisRun run = new AnalysisRun();
        run.setSnapshotId(snapshotId);
        run.setStatus(AnalysisRunStatus.RUNNING);
        run.setArtifactSource(artifactSource);
        run.setStartedAt(OffsetDateTime.now());
        return analysisRunRepository.saveAndFlush(run);
    }

    /**
     * Ghi kết cục ĐÃ QUYẾT ĐỊNH XONG. Method này không suy luận gì — mọi lựa chọn trạng thái nằm ở
     * {@code AnalysisRunService}, xem Javadoc {@link RunOutcome} để biết vì sao ranh giới đó quan
     * trọng.
     *
     * <p>{@code REQUIRES_NEW} vì nó được gọi cả từ nhánh xử lý lỗi, nơi có thể đang không có
     * transaction nào hoặc transaction hiện tại đã hỏng.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisRun finalizeRun(UUID runId, RunOutcome outcome) {
        AnalysisRun run = analysisRunRepository.findById(runId).orElseThrow();
        run.setStatus(outcome.status());
        run.setCleanAckNote(outcome.cleanAckNote());
        run.setErrorMessage(outcome.errorMessage());
        run.setFinishedAt(OffsetDateTime.now());

        MaterializationResult result = outcome.result();
        if (result != null) {
            // Ghi bộ đếm KỂ CẢ khi FAILED: "hỏng vì 100% đường dẫn không đối chiếu được" chỉ đọc
            // ra được từ hai con số này. ck_run_paths_not_all_unresolved chỉ ràng buộc SUCCEEDED
            // nên ghi chúng ở nhánh FAILED là an toàn.
            run.setFindingCount(result.findingCount());
            run.setLocationCount(result.locationCount());
            run.setUnresolvedLocationCount(result.unresolvedLocationCount());
            // Mẫu số của ck_run_links_not_silently_empty. Phải ghi Ở ĐÂY chứ không để
            // EvidenceLinker tự UPDATE analysis_run: method này là REQUIRES_NEW, nó TREO
            // transaction ngoài rồi đọc lại hàng trong một transaction mới — nên một UPDATE chưa
            // commit từ bên ngoài là vô hình với nó, và dirty-check của Hibernate sẽ ghi đè cả
            // hàng bằng giá trị cũ. Bộ đếm về 0 trong im lặng thì mọi guard "0 link = lỗi" bị vô
            // hiệu mà nhìn vẫn như đang chạy.
            run.setCodeContextCount(result.codeContextCount());
        }
        if (outcome.toolVersions() != null && !outcome.toolVersions().isEmpty()) {
            run.setToolVersions(new LinkedHashMap<String, Object>(outcome.toolVersions()));
        }
        if (outcome.links() != null) {
            run.setLinkCount(outcome.links().linkCount());
            run.setUnlinkedWarningCount(outcome.links().unlinkedWarningCount());
            run.setLinkCountsByMethod(new LinkedHashMap<>(outcome.links().countByMethod()));
        }
        SandboxInfo sandbox = outcome.sandbox();
        if (sandbox != null) {
            run.setAnalyzerImageDigest(sandbox.imageDigest());
            run.setSourceRoot(sandbox.sourceRoot());
            // ck_run_sandbox_needs_canary đòi TRUE cho run SUCCEEDED sinh từ sandbox — ghi ở đây
            // chứ không suy ở tầng nào khác, để chỉ có MỘT chỗ quyết định giá trị này.
            run.setCanaryOk(sandbox.canaryOk());
            run.setCanaryFindingCount(sandbox.canaryFindingCount());
            run.setSandboxDurationMs(sandbox.durationMs());
        }
        return run;
    }

    /** Đọc lại run sau khi transaction ghi đã commit. */
    @Transactional(readOnly = true)
    public AnalysisRun get(UUID runId) {
        return analysisRunRepository.findById(runId).orElseThrow();
    }
}
