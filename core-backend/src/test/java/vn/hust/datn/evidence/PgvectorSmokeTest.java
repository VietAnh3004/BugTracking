package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chứng minh chuỗi pgvector chạy được, TRƯỚC KHI chọn model embedding (tuần 10-11) — không tạo
 * bảng evidence_embedding thật ở tuần 3 vì số chiều vector phụ thuộc model chưa chọn. Test này
 * tự tạo bảng tạm, không đụng tới V2/V3 migration, và dọn dẹp sau khi chạy.
 *
 * <p>Cũng là nơi xác nhận image {@code pgvector/pgvector:pg16} thay thế đúng vai trò của
 * infra/init/01-extensions.sql (không chạy trong Testcontainers) — nếu thiếu, CREATE EXTENSION
 * ở đây sẽ throw ngay dòng đầu.
 */
class PgvectorSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tuTaoExtensionBangTamVaTruyVanCosine() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("CREATE TEMP TABLE embedding_smoke (id INT PRIMARY KEY, v VECTOR(3))");
        jdbcTemplate.update("INSERT INTO embedding_smoke VALUES (1, '[1,0,0]'), (2, '[0,1,0]')");

        Integer nearestToOrigin = jdbcTemplate.queryForObject(
                "SELECT id FROM embedding_smoke ORDER BY v <=> '[1,0,0]' LIMIT 1", Integer.class);

        assertThat(nearestToOrigin).isEqualTo(1);
    }
}
