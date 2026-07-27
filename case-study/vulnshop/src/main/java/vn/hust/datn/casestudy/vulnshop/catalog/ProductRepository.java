package vn.hust.datn.casestudy.vulnshop.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.hust.datn.casestudy.vulnshop.domain.Product;
import vn.hust.datn.casestudy.vulnshop.domain.SortField;

/** Truy vấn bảng {@code products}. */
@Repository
public class ProductRepository {

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("price"), rs.getString("category"));

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * SEEDED — lỗi thật: SQL injection qua từ khoá tìm kiếm.
     *
     * <p>{@code keyword} do người dùng nhập và được nối trực tiếp vào mệnh đề LIKE.
     */
    public List<Product> searchByKeyword(String keyword) {
        String sql = "SELECT * FROM products WHERE name LIKE '%" + keyword + "%'";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * SEEDED — false positive: nối chuỗi vào SQL nhưng giá trị đến từ enum.
     *
     * <p>Công cụ phân tích tĩnh nhìn thấy chuỗi SQL được ghép động và nhiều khả năng báo SQL
     * injection. Thực tế {@code sortField.column()} chỉ trả về một trong ba hằng số đã định nghĩa
     * trong {@link SortField}; người dùng không thể truyền chuỗi tuỳ ý vào đây vì tham số đã được
     * ràng buộc kiểu ở tầng controller.
     *
     * <p>Đây đúng là kiểu cảnh báo mà người phát triển phải đọc ngữ cảnh xung quanh mới kết luận
     * được — trường hợp tiêu biểu cho thấy vì sao AI cần bằng chứng chứ không chỉ nội dung cảnh báo.
     *
     * <p>Lưu ý: ORDER BY không dùng được tham số ràng buộc trong JDBC, nên whitelist bằng enum là
     * cách xử lý đúng chứ không phải giải pháp tạm.
     */
    public List<Product> findAllSorted(SortField sortField) {
        String sql = "SELECT * FROM products ORDER BY " + sortField.column();
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public List<Product> findByCategory(String category) {
        return jdbcTemplate.query("SELECT * FROM products WHERE category = ?", ROW_MAPPER, category);
    }

    public void insert(Product product) {
        jdbcTemplate.update(
                "INSERT INTO products (id, name, price, category) VALUES (?, ?, ?, ?)",
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory());
    }

    public BigDecimal totalInventoryValue() {
        BigDecimal total = jdbcTemplate.queryForObject("SELECT SUM(price) FROM products", BigDecimal.class);
        return total == null ? BigDecimal.ZERO : total;
    }
}
