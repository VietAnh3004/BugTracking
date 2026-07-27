package vn.hust.datn.casestudy.vulnshop.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.hust.datn.casestudy.vulnshop.domain.Product;

/** Tính toán trên giỏ hàng. */
@Service
public class CartService {

    /**
     * SEEDED — lỗi thật: chia cho 0 khi giỏ hàng rỗng.
     *
     * <p>{@code items.size()} bằng 0 thì {@code BigDecimal.divide} ném ArithmeticException. Giỏ
     * rỗng là trạng thái hoàn toàn bình thường, không phải ngoại lệ.
     *
     * <p>Kỳ vọng phân tích tĩnh không bắt được vì kích thước danh sách chỉ biết được lúc chạy.
     */
    public BigDecimal averagePrice(List<Product> items) {
        BigDecimal total = items.stream()
                .map(Product::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal totalPrice(List<Product> items) {
        return items.stream().map(Product::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Áp mã giảm giá theo phần trăm.
     *
     * <p>Không có lỗi cài đặt ở đây — dùng để đối chiếu, cho thấy không phải hàm nào cũng có vấn đề.
     */
    public BigDecimal applyDiscount(BigDecimal total, int percentOff) {
        if (percentOff < 0 || percentOff > 100) {
            throw new IllegalArgumentException("percentOff phai nam trong khoang 0..100");
        }
        BigDecimal multiplier = BigDecimal.valueOf(100 - percentOff).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return total.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
