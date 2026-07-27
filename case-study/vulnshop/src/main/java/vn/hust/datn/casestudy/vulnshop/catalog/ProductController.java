package vn.hust.datn.casestudy.vulnshop.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hust.datn.casestudy.vulnshop.domain.Product;
import vn.hust.datn.casestudy.vulnshop.domain.SortField;

/** Điểm vào HTTP cho catalogue sản phẩm. */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Tìm kiếm — {@code keyword} đi thẳng vào câu lệnh SQL bị nối chuỗi. */
    @GetMapping("/search")
    public List<Product> search(@RequestParam String keyword) {
        return productRepository.searchByKeyword(keyword);
    }

    /**
     * Liệt kê có sắp xếp.
     *
     * <p>Tham số được khai báo kiểu {@link SortField}, nên Spring từ chối mọi giá trị không nằm
     * trong ba hằng số của enum trước khi vào được hàm này. Đây là lý do việc nối chuỗi trong
     * {@code findAllSorted} là an toàn — nhưng công cụ phân tích tĩnh không nhìn thấy ràng buộc
     * này vì nó nằm ở tầng khác.
     */
    @GetMapping
    public List<Product> list(@RequestParam(defaultValue = "NAME") SortField sortBy) {
        return productRepository.findAllSorted(sortBy);
    }
}
