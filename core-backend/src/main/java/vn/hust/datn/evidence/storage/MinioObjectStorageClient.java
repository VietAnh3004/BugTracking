package vn.hust.datn.evidence.storage;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.StorageProperties;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;

/**
 * DUY NHẤT lớp này được import {@code io.minio.*} trong toàn dự án — đổi sang S3 thật (hay bất kỳ
 * SDK nào khác) sau này chỉ cần viết thêm một implementation của {@link ObjectStorageClient}, các
 * service gọi interface không phải sửa gì.
 *
 * <p>{@code GetObjectResponse} BẮT BUỘC try-with-resources — không đóng nó sau vài chục request
 * đọc file sẽ cạn connection pool của okhttp bên trong SDK MinIO, và ứng dụng TREO không có lỗi
 * rõ ràng nào (xem CLAUDE.md).
 *
 * <p>{@code @Profile("!test")}: nếu không loại khỏi profile {@code test}, bean này vẫn được khởi
 * tạo song song với {@code InMemoryObjectStorageClient} (dù không phải {@code @Primary}) —
 * {@code @PostConstruct} vẫn chạy, cố kết nối MinIO thật không tồn tại trong bộ test, và làm
 * TOÀN BỘ Spring context thất bại ngay lúc khởi động (không phải lỗi riêng của test nào cả).
 */
@Component
@Profile("!test")
public class MinioObjectStorageClient implements ObjectStorageClient {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorageClient(StorageProperties storageProperties) {
        this.client = MinioClient.builder()
                .endpoint(storageProperties.endpoint())
                .credentials(storageProperties.accessKey(), storageProperties.secretKey())
                .build();
        this.bucket = storageProperties.bucket();
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new ObjectStorageException("Không tạo/kiểm tra được bucket '%s'".formatted(bucket), e);
        }
    }

    @Override
    public void putObject(String key, byte[] content, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new ObjectStorageException("Không ghi được object '%s'".formatted(key), e);
        }
    }

    @Override
    public StoredObject getObject(String key) {
        try (GetObjectResponse response =
                client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = response.readAllBytes();
            String contentType = response.headers().get("Content-Type");
            return new StoredObject(bytes, contentType);
        } catch (Exception e) {
            throw new ObjectStorageException("Không đọc được object '%s'".formatted(key), e);
        }
    }

    @Override
    public void deleteObjectsByPrefix(String prefix) {
        try {
            Iterable<Result<Item>> listed = client.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());

            List<DeleteObject> toDelete = new ArrayList<>();
            for (Result<Item> result : listed) {
                toDelete.add(new DeleteObject(result.get().objectName()));
            }
            if (toDelete.isEmpty()) {
                return;
            }

            Iterable<Result<DeleteError>> errors = client.removeObjects(
                    RemoveObjectsArgs.builder().bucket(bucket).objects(toDelete).build());
            // removeObjects() lười (lazy) - phải duyệt hết iterator để lỗi thật sự được ném ra.
            for (Result<DeleteError> error : errors) {
                error.get();
            }
        } catch (Exception e) {
            throw new ObjectStorageException("Không xoá được object có prefix '%s'".formatted(prefix), e);
        }
    }
}
