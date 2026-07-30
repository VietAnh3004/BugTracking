package vn.hust.datn.evidence.exception;

/** short_id không tồn tại ở đâu cả — tương ứng {@code ClaimCitationResolution.UNKNOWN_ID}. */
public class EvidenceNotFoundException extends RuntimeException {

    public EvidenceNotFoundException(String shortId) {
        super("Không tìm thấy evidence với short_id '%s'".formatted(shortId));
    }
}
