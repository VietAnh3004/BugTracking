package vn.hust.datn.casestudy.vulnshop.user;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.hust.datn.casestudy.vulnshop.domain.User;

/** Truy vấn bảng {@code users}. */
@Repository
public class UserRepository {

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"));

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * SEEDED — lỗi thật: SQL injection.
     *
     * <p>{@code username} đến thẳng từ tham số HTTP và được nối vào câu lệnh. Nhập
     * {@code ' OR '1'='1} sẽ trả về mọi tài khoản. Kỳ vọng Semgrep và find-sec-bugs cùng bắt
     * (SQL_INJECTION_JDBC / formatted-sql-string).
     */
    public List<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /** Truy vấn an toàn, dùng tham số ràng buộc — để đối chiếu với hàm bên trên. */
    public List<User> findByRole(String role) {
        return jdbcTemplate.query("SELECT * FROM users WHERE role = ?", ROW_MAPPER, role);
    }

    public void insert(User user) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                user.id(),
                user.username(),
                user.email(),
                user.passwordHash(),
                user.role());
    }
}
