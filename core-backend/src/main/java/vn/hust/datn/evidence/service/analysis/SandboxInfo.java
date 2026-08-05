package vn.hust.datn.evidence.service.analysis;

/**
 * Dấu vết của một lần chạy sandbox, ghi kèm vào {@code analysis_run}.
 *
 * <p>null với run nạp artifact thủ công — và đó là thông tin thật, không phải thiếu dữ liệu: ở
 * đường nạp tay không có image nào để ghi digest và không có canary nào để kiểm.
 *
 * @param imageDigest  digest chứ không phải tag, xem {@code AnalysisRun.analyzerImageDigest}
 * @param canaryOk     ràng buộc {@code ck_run_sandbox_needs_canary} đòi TRUE cho run SUCCEEDED
 */
public record SandboxInfo(
        String imageDigest, String sourceRoot, boolean canaryOk, int canaryFindingCount, long durationMs) {}
