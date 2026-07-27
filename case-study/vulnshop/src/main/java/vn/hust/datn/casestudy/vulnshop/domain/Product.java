package vn.hust.datn.casestudy.vulnshop.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Sản phẩm trong catalogue. */
public class Product {

    private final long id;
    private final String name;
    private final BigDecimal price;
    private final String category;

    public Product(long id, String name, BigDecimal price, String category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    /**
     * SEEDED — lỗi thật: equals() được ghi đè nhưng hashCode() thì không.
     *
     * <p>Hai Product bằng nhau sẽ cho hai hashCode khác nhau, nên đặt vào HashSet hoặc dùng làm
     * khoá HashMap sẽ cho kết quả sai. Kỳ vọng SpotBugs bắt bằng rule HE_EQUALS_USE_HASHCODE.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Product product)) {
            return false;
        }
        return id == product.id && Objects.equals(name, product.name);
    }
}
