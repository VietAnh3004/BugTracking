package vn.hust.datn.evidence.storage;

/**
 * Cổng ra object storage — service không bao giờ biết implementation là MinIO hay S3 thật. Đổi
 * nhà cung cấp = viết thêm một implementation, không sửa service nào gọi interface này.
 */
public interface ObjectStorageClient {

    void putObject(String key, byte[] content, String contentType);

    StoredObject getObject(String key);

    /** Xoá toàn bộ object có key bắt đầu bằng {@code prefix} — dùng khi dọn rác snapshot FAILED. */
    void deleteObjectsByPrefix(String prefix);
}
