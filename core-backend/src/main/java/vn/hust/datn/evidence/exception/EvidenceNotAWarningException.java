package vn.hust.datn.evidence.exception;

/** {@code get_warning} được gọi lên một evidence không phải STATIC_WARNING. Lỗi đầu vào, 400. */
public class EvidenceNotAWarningException extends RuntimeException {

    public EvidenceNotAWarningException(String shortId) {
        super("Evidence '%s' không phải cảnh báo tĩnh (STATIC_WARNING)".formatted(shortId));
    }
}
