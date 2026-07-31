package vn.hust.datn.evidence.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import vn.hust.datn.evidence.config.properties.StorageProperties;

/**
 * Ngoại lệ DUY NHẤT trả chi phí container MinIO thật trong bộ test — mọi test khác của luồng
 * upload (zip-slip, giới hạn, ranh giới transaction) dùng fake trong bộ nhớ
 * ({@code InMemoryObjectStorageClient}, xem Javadoc ở đó). Lớp này kiểm chứng đúng những gì fake
 * KHÔNG THỂ: hành vi thật của SDK MinIO — mã hoá URL cho key có ký tự đặc biệt, round-trip byte
 * nhị phân, và việc lỗi SDK được dịch đúng sang {@link ObjectStorageException}.
 *
 * <p>JUnit THUẦN, không {@code @SpringBootTest} — khởi tạo {@link MinioObjectStorageClient} trực
 * tiếp, gọi {@code ensureBucketExists()} thủ công (không có Spring để tự gọi {@code @PostConstruct}).
 * Container tự khởi trong static initializer, không bao giờ {@code stop()} — cùng "singleton
 * container pattern" đã áp dụng cho Postgres ở {@code AbstractIntegrationTest}, tự viết riêng ở
 * đây vì đây là JUnit thuần, không kế thừa được base class đó.
 *
 * <p>Ghim đúng tag {@code minio/minio:RELEASE.2025-04-22T22-12-26Z} như trong
 * {@code infra/docker-compose.yml} — không kéo thêm image thứ hai, và test đúng bản đang chạy
 * thật. Nếu image không kéo được trên máy này, xem CLAUDE.md (mirror.gcr.io).
 */
class MinioObjectStorageClientTest {

    private static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"));

    static {
        MINIO.start();
    }

    private final MinioObjectStorageClient client = newClient();

    private MinioObjectStorageClient newClient() {
        StorageProperties properties =
                new StorageProperties(MINIO.getS3URL(), MINIO.getUserName(), MINIO.getPassword(), "it-test-bucket");
        MinioObjectStorageClient client = new MinioObjectStorageClient(properties);
        client.ensureBucketExists();
        return client;
    }

    @Test
    void ghiVaDocLaiDungByteNhiPhan() {
        byte[] binary = new byte[] {0x00, 0x01, (byte) 0xFF, 0x7F, (byte) 0x80};
        String key = "snapshots/" + UUID.randomUUID() + "/binary.dat";

        client.putObject(key, binary, "application/octet-stream");
        StoredObject result = client.getObject(key);

        assertThat(result.content()).isEqualTo(binary);
    }

    @Test
    void keyChuaKyTuDacBietVanDocDungLaiDuoc() {
        // Dấu cách, '+', '#', và ký tự Unicode - MinIO SDK phải tự mã hoá URL đúng cách; fake
        // trong bộ nhớ (dùng Map<String,...> Java) không bao giờ bắt được lớp lỗi này.
        String key = "snapshots/%s/src/main/Tệp có dấu + đặc biệt #1.java".formatted(UUID.randomUUID());
        byte[] content = "class A {}".getBytes(StandardCharsets.UTF_8);

        client.putObject(key, content, "text/x-java-source");
        StoredObject result = client.getObject(key);

        assertThat(new String(result.content(), StandardCharsets.UTF_8)).isEqualTo("class A {}");
    }

    @Test
    void doiTuongKhongTonTaiNemObjectStorageExceptionCuaTaKhongPhaiRoSdk() {
        assertThatThrownBy(() -> client.getObject("khong-ton-tai/" + UUID.randomUUID()))
                .isInstanceOf(ObjectStorageException.class)
                .isNotInstanceOf(io.minio.errors.ErrorResponseException.class);
    }

    @Test
    void xoaTheoPrefixXoaHetCacObjectConLaiKhongDungPrefix() {
        String prefix = "snapshots/" + UUID.randomUUID() + "/";
        client.putObject(prefix + "a.txt", "a".getBytes(StandardCharsets.UTF_8), "text/plain");
        client.putObject(prefix + "sub/b.txt", "b".getBytes(StandardCharsets.UTF_8), "text/plain");
        String otherKey = "snapshots/" + UUID.randomUUID() + "/c.txt";
        client.putObject(otherKey, "c".getBytes(StandardCharsets.UTF_8), "text/plain");

        client.deleteObjectsByPrefix(prefix);

        assertThatThrownBy(() -> client.getObject(prefix + "a.txt")).isInstanceOf(ObjectStorageException.class);
        assertThatThrownBy(() -> client.getObject(prefix + "sub/b.txt")).isInstanceOf(ObjectStorageException.class);
        // Object ngoài prefix không bị đụng tới.
        assertThat(client.getObject(otherKey).content()).isEqualTo("c".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void tuTaoBucketKhiChuaCo() {
        // ensureBucketExists() đã chạy trong newClient() lúc setup field - nếu nó không tự tạo
        // được bucket thì MỌI test khác trong lớp này đã đỏ trước khi chạy tới đây. Test này chỉ
        // xác nhận tường minh bằng cách gọi lại lần nữa (idempotent) và ghi thử một object.
        client.ensureBucketExists();
        String key = "smoke/" + UUID.randomUUID();
        client.putObject(key, "ok".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(client.getObject(key).content()).isEqualTo("ok".getBytes(StandardCharsets.UTF_8));
    }
}
