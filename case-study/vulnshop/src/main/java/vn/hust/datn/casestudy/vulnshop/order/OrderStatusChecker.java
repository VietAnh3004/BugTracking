package vn.hust.datn.casestudy.vulnshop.order;

/** Kiểm tra trạng thái đơn hàng. */
public final class OrderStatusChecker {

    private OrderStatusChecker() {}

    /**
     * SEEDED — lỗi thật: so sánh chuỗi bằng {@code ==}.
     *
     * <p>Toán tử {@code ==} so sánh tham chiếu chứ không so sánh nội dung. Nó vô tình chạy đúng
     * với chuỗi hằng nhờ cơ chế gộp chuỗi của JVM, nhưng sai ngay khi chuỗi đến từ cơ sở dữ liệu
     * hay từ request. Đây là kiểu lỗi rất khó chẩn đoán vì test đơn giản có thể vẫn xanh.
     *
     * <p>Kỳ vọng SpotBugs bắt (ES_COMPARING_STRINGS_WITH_EQ).
     */
    public static boolean isShipped(String status) {
        return status == "SHIPPED";
    }

    /** So sánh đúng cách — để đối chiếu. */
    public static boolean isCancelled(String status) {
        return "CANCELLED".equals(status);
    }

    public static boolean isFinal(String status) {
        return isShipped(status) || isCancelled(status);
    }
}
