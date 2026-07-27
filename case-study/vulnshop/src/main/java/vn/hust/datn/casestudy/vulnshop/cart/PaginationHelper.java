package vn.hust.datn.casestudy.vulnshop.cart;

/** Tính toán phân trang cho danh sách sản phẩm. */
public final class PaginationHelper {

    private PaginationHelper() {}

    /**
     * SEEDED — lỗi thật: sai một đơn vị (off-by-one).
     *
     * <p>Với 20 phần tử và mỗi trang 10 phần tử, kết quả đúng phải là 2 trang, nhưng công thức
     * {@code totalItems / pageSize + 1} cho ra 3. Trang cuối luôn rỗng.
     *
     * <p>Kỳ vọng phân tích tĩnh KHÔNG bắt được — đây là lỗi logic nghiệp vụ, công cụ không biết
     * đáp án đúng phải là bao nhiêu. Chỉ test mới phát hiện.
     */
    public static int totalPages(int totalItems, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize phai lon hon 0");
        }
        return totalItems / pageSize + 1;
    }

    /** Chỉ số phần tử đầu tiên của trang, tính từ 0. */
    public static int firstIndexOfPage(int page, int pageSize) {
        return (page - 1) * pageSize;
    }
}
