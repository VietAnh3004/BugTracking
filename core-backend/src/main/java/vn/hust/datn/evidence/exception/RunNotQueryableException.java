package vn.hust.datn.evidence.exception;

import java.util.UUID;

import vn.hust.datn.evidence.domain.AnalysisRunStatus;

/**
 * Run chưa ở trạng thái cho phép truy vấn (chỉ SUCCEEDED/SUCCEEDED_CLEAN). 409 Conflict.
 *
 * <p>Ngăn "rỗng vì chưa build" lẫn với "rỗng vì đúng là rỗng": nếu tool được gọi trên một run
 * đang RUNNING hoặc đã FAILED, kết quả rỗng không có nghĩa gì — client phải biết run chưa sẵn
 * sàng thay vì hiểu nhầm thành "không có bằng chứng liên quan". Nới rộng nguyên tắc "0 cảnh báo =
 * lỗi" (ADR 0004 §5, ép ở tầng DB) lên tầng API.
 */
public class RunNotQueryableException extends RuntimeException {

    public RunNotQueryableException(UUID runId, AnalysisRunStatus status) {
        super("analysis_run %s đang ở trạng thái %s, chưa thể truy vấn evidence".formatted(runId, status));
    }
}
