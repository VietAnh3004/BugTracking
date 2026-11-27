package vn.hust.datn.evidence.exception;

import java.util.UUID;

/** Bấm "Chạy" khi thực nghiệm của run này đang chạy — 409, không phải mở thêm một job song song. */
public class ExperimentAlreadyRunningException extends RuntimeException {

    public ExperimentAlreadyRunningException(UUID runId) {
        super("Thực nghiệm của lần phân tích %s đang chạy".formatted(runId));
    }
}
