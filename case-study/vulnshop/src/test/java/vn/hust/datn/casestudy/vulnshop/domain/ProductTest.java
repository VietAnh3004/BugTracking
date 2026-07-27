package vn.hust.datn.casestudy.vulnshop.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static Product product(long id, String name) {
        return new Product(id, name, new BigDecimal("10.00"), "chung");
    }

    @Test
    void equals_cungIdVaTen_bangNhau() {
        assertTrue(product(1, "Ao thun").equals(product(1, "Ao thun")));
    }

    /**
     * TEST NAY CO TINH HONG.
     *
     * <p>equals() duoc ghi de nhung hashCode() thi khong, nen hai Product bang nhau lai roi vao
     * hai o khac nhau cua HashSet. Set nhan hai phan tu thay vi mot.
     *
     * <p>Sinh ra FAILED_TEST tro ve dong them phan tu - bang chung ung ho canh bao
     * HE_EQUALS_USE_HASHCODE cua SpotBugs.
     */
    @Test
    @DisplayName("Hai san pham bang nhau chi duoc tinh la mot trong HashSet")
    void hashSet_haiSanPhamBangNhau_chiGiuMot() {
        Set<Product> products = new HashSet<>();
        products.add(product(1, "Ao thun"));
        products.add(product(1, "Ao thun"));

        assertEquals(1, products.size());
    }

    @Test
    void getters_traVeDungGiaTri() {
        Product p = new Product(7, "Quan jean", new BigDecimal("250.00"), "quan-ao");

        assertEquals(7, p.getId());
        assertEquals("Quan jean", p.getName());
        assertEquals(new BigDecimal("250.00"), p.getPrice());
        assertEquals("quan-ao", p.getCategory());
    }
}
