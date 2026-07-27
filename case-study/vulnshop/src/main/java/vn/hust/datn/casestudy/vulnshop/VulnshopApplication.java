package vn.hust.datn.casestudy.vulnshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ứng dụng case study cho đồ án tốt nghiệp.
 *
 * <p><b>CẢNH BÁO:</b> mã nguồn trong dự án này cố tình chứa lỗi bảo mật và lỗi logic. Nó là
 * <i>đối tượng phân tích</i> của nền tảng, không phải một phần của sản phẩm và tuyệt đối không
 * được dùng làm mẫu cho mã thật.
 *
 * <p>Danh mục lỗi được cài cùng lý do và kỳ vọng nhãn (true positive / false positive) ghi đầy đủ
 * trong {@code SEEDED.md}.
 */
@SpringBootApplication
public class VulnshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(VulnshopApplication.class, args);
    }
}
