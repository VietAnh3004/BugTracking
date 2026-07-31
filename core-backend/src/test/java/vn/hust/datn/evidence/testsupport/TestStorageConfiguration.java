package vn.hust.datn.evidence.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.hust.datn.evidence.storage.ObjectStorageClient;

/**
 * Thay {@link vn.hust.datn.evidence.storage.MinioObjectStorageClient} bằng fake trong bộ nhớ cho
 * toàn bộ bộ test dùng chung ({@code AbstractIntegrationTest}) — không kéo thêm một container
 * MinIO cho các test không đụng tới object storage (đa số). Xem Javadoc
 * {@link InMemoryObjectStorageClient}.
 */
@TestConfiguration
public class TestStorageConfiguration {

    @Bean
    @Primary
    public ObjectStorageClient objectStorageClient() {
        return new InMemoryObjectStorageClient();
    }
}
