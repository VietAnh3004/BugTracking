package vn.hust.datn.evidence.testsupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.storage.ObjectStorageException;
import vn.hust.datn.evidence.storage.StoredObject;

/**
 * Fake trong bộ nhớ, KHÔNG dùng MinIO container trong bộ test dùng chung — xem
 * {@code TestStorageConfiguration} và {@code MinioObjectStorageClientTest} (Javadoc ở đó giải
 * thích đầy đủ lý do chia theo cái mỗi bên bác bỏ được: fake nhanh/tất định cho logic upload
 * (zip-slip, giới hạn, ranh giới transaction), container thật chỉ cho đúng một test round-trip
 * SDK MinIO).
 *
 * <p>{@link #failOnKeyContaining} cho phép mô phỏng lỗi ở ĐÚNG một file giữa chừng upload — thứ
 * container thật rất khó làm được trong test.
 */
public class InMemoryObjectStorageClient implements ObjectStorageClient {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private volatile String failOnKeyContaining;

    @Override
    public void putObject(String key, byte[] content, String contentType) {
        if (failOnKeyContaining != null && key.contains(failOnKeyContaining)) {
            throw new ObjectStorageException("Loi gia lap (test) khi ghi object '%s'".formatted(key));
        }
        objects.put(key, new StoredObject(content, contentType));
    }

    @Override
    public StoredObject getObject(String key) {
        StoredObject object = objects.get(key);
        if (object == null) {
            throw new ObjectStorageException("Khong tim thay object '%s'".formatted(key));
        }
        return object;
    }

    @Override
    public void deleteObjectsByPrefix(String prefix) {
        objects.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public boolean containsKey(String key) {
        return objects.containsKey(key);
    }

    public int size() {
        return objects.size();
    }

    /** Ném {@link ObjectStorageException} cho lần {@code putObject} kế tiếp có key chứa {@code fragment}. */
    public void failOnKeyContaining(String fragment) {
        this.failOnKeyContaining = fragment;
    }

    public void reset() {
        objects.clear();
        failOnKeyContaining = null;
    }
}
