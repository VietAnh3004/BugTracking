package vn.hust.datn.evidence.storage;

/**
 * Bọc mọi lỗi từ SDK object storage (MinIO hay bất kỳ implementation nào sau này) thành một loại
 * lỗi duy nhất, không phụ thuộc SDK cụ thể — service gọi {@link ObjectStorageClient} không bao
 * giờ thấy {@code ErrorResponseException}/{@code MinioException} rò ra ngoài.
 */
public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectStorageException(String message) {
        super(message);
    }
}
