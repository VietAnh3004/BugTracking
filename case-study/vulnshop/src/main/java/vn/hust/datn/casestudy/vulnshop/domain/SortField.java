package vn.hust.datn.casestudy.vulnshop.domain;

/**
 * Các cột được phép sắp xếp trong catalogue.
 *
 * <p>Tồn tại để giới hạn giá trị đưa vào mệnh đề ORDER BY: người dùng gửi lên tên hằng số, không
 * gửi thẳng tên cột. Đây là whitelist ở tầng kiểu dữ liệu.
 */
public enum SortField {
    NAME("name"),
    PRICE("price"),
    CATEGORY("category");

    private final String column;

    SortField(String column) {
        this.column = column;
    }

    /** Tên cột tương ứng trong bảng {@code products}. */
    public String column() {
        return column;
    }
}
