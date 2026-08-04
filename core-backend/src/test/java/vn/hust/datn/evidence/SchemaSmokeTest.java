package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bước 0 của tuần 3: chứng minh riêng hạ tầng test chạy được, TRƯỚC KHI đụng tới entity nào.
 *
 * <p>Đây là lần đầu tiên dự án có {@code @SpringBootTest} + Testcontainers thật (0 entity JPA
 * tồn tại khi viết test này). Nếu test này đỏ, nguyên nhân chắc chắn là hạ tầng — Docker, image,
 * Flyway — không thể lẫn với lỗi mapping entity. Việc tách bước này ra khỏi các bước sau (entity
 * đầu tiên, rồi schema V2) giúp khoanh vùng lỗi khi mọi thứ đều mới cùng lúc.
 */
class SchemaSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ketNoiDuocVaChayXongFlywayV1VaV2() {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);

        Integer flywayAppliedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(flywayAppliedCount).isGreaterThanOrEqualTo(5); // V1..V5

        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'evidence_record'
                """,
                Integer.class);
        assertThat(tableCount).isEqualTo(1);
    }

    /**
     * Năm bảng thô của V5. Liệt kê tường minh chứ không đếm tổng số bảng: đếm tổng thì thêm bất
     * kỳ bảng nào cũng phải sửa test, còn thiếu đúng một bảng trong năm bảng này thì không phát
     * hiện được.
     */
    @Test
    void namBangThoCuaV5DeuTonTai() {
        for (String table :
                new String[] {"warning_location", "test_case", "stack_frame", "coverage_line", "coverage_method"}) {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                    """,
                    Integer.class,
                    table);
            assertThat(count).as("Thieu bang '%s' cua V5", table).isEqualTo(1);
        }
    }
}
