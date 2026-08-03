package vn.hust.datn.evidence.exception;

/**
 * Zip vượt một trong các ngưỡng chống zip-bomb của {@link
 * vn.hust.datn.evidence.config.properties.UploadProperties} — số file, tổng byte đã giải nén, hay
 * tỷ lệ nén. Ánh xạ sang HTTP 413 (Payload Too Large), cùng nhóm ngữ nghĩa với
 * {@code MaxUploadSizeExceededException} dù nguyên nhân khác nhau (kích thước sau giải nén, không
 * phải kích thước request thô).
 */
public class ArchiveLimitExceededException extends RuntimeException {

    public ArchiveLimitExceededException(String message) {
        super(message);
    }
}
