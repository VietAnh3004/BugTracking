package vn.hust.datn.evidence.exception;

/** short_id tồn tại nhưng ở run khác — tương ứng {@code ClaimCitationResolution.WRONG_RUN}. */
public class EvidenceWrongRunException extends RuntimeException {

    public EvidenceWrongRunException(String shortId) {
        super("Evidence '%s' không thuộc analysis_run được yêu cầu".formatted(shortId));
    }
}
