package vn.hust.datn.casestudy.vulnshop.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hust.datn.casestudy.vulnshop.domain.Product;

class CartServiceTest {

    private final CartService cartService = new CartService();

    private static Product product(long id, String price) {
        return new Product(id, "San pham " + id, new BigDecimal(price), "chung");
    }

    @Test
    void averagePrice_gioCoHang_tinhDungTrungBinh() {
        List<Product> items = List.of(product(1, "100.00"), product(2, "200.00"));

        assertEquals(new BigDecimal("150.00"), cartService.averagePrice(items));
    }

    /**
     * TEST NAY CO TINH HONG.
     *
     * <p>Gio rong lam averagePrice chia cho 0 -> ArithmeticException. Gio rong la trang thai binh
     * thuong, dang le phai tra ve 0 chu khong nem ngoai le.
     *
     * <p>Sinh ra FAILED_TEST kem STACK_TRACE tro ve CartService.averagePrice.
     */
    @Test
    @DisplayName("Gio rong tra ve 0 thay vi nem ngoai le")
    void averagePrice_gioRong_traVeKhong() {
        assertEquals(BigDecimal.ZERO, cartService.averagePrice(List.of()));
    }

    @Test
    void totalPrice_congDungTongTien() {
        List<Product> items = List.of(product(1, "10.50"), product(2, "20.25"));

        assertEquals(new BigDecimal("30.75"), cartService.totalPrice(items));
    }

    @Test
    void totalPrice_gioRong_traVeKhong() {
        assertEquals(BigDecimal.ZERO, cartService.totalPrice(List.of()));
    }

    @Test
    void applyDiscount_giamMuoiPhanTram() {
        assertEquals(new BigDecimal("90.00"), cartService.applyDiscount(new BigDecimal("100.00"), 10));
    }
}
