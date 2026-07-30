package vn.hust.datn.evidence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base cho mọi integration test cần Postgres thật.
 *
 * <p>Dùng image {@code pgvector/pgvector:pg16} — cùng image với infra/docker-compose.yml và
 * production — thay vì {@code postgres:16} mặc định của Testcontainers. Lý do: extension {@code
 * vector} được cài sẵn trong image, còn {@code infra/init/01-extensions.sql} (nơi tạo extension
 * ở môi trường dev/prod) KHÔNG chạy trong Testcontainers.
 *
 * <p><b>Cố tình KHÔNG dùng {@code @Testcontainers}/{@code @Container}</b>: annotation đó (kể cả
 * trên field {@code static}) khiến Testcontainers tự gọi {@code stop()} trong {@code afterAll}
 * của MỖI class test kế thừa — vì {@code @Testcontainers} được đăng ký lại theo từng subclass.
 * Field static bị 4-5 subclass dùng chung nên class đầu tiên chạy xong sẽ dừng container, các
 * class sau connect vào container đã chết (triệu chứng: {@code CannotGetJdbcConnection} timeout
 * đúng 30s — connectionTimeout mặc định của HikariCP — chỉ xảy ra khi chạy CẢ BỘ test, không xảy
 * ra khi chạy một class riêng lẻ). Đây là "singleton container pattern" chính thức của
 * Testcontainers: tự khởi động một lần trong static initializer, không bao giờ gọi
 * {@code stop()} — Ryuk (resource reaper) dọn dẹp khi JVM thoát.
 */
@ActiveProfiles("test")
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
