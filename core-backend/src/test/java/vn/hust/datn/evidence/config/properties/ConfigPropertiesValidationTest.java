package vn.hust.datn.evidence.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

/**
 * Cấu hình sai phải bị chặn ngay lúc khởi động.
 *
 * <p>Cả ba service đều dựa vào tính chất fail-fast này. Nếu nó âm thầm mất đi, một biến môi
 * trường gõ nhầm sẽ chỉ lộ ra giữa lúc người dùng đang phân tích — thay vì ngay lúc deploy.
 *
 * <p>Test chạy thẳng trên Bean Validation, không nạp Spring context: nhanh, không cần cơ sở dữ
 * liệu, và kiểm đúng thứ cần kiểm là các ràng buộc trên bản ghi cấu hình.
 */
class ConfigPropertiesValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void corsHopLeKhiCoItNhatMotOrigin() {
        CorsProperties properties = new CorsProperties(List.of("https://analysis.example.com"));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void corsRongThiBiTuChoi() {
        // Danh sách rỗng nghĩa là không frontend nào gọi được API — gần như chắc chắn là
        // cấu hình thiếu chứ không phải chủ ý.
        CorsProperties properties = new CorsProperties(List.of());

        assertThat(validator.validate(properties)).hasSize(1);
    }

    @Test
    void jwtSecretNgan32KyTuThiBiTuChoi() {
        // HS256 cần khoá tối thiểu 256 bit; khoá ngắn hơn làm chữ ký yếu đi.
        JwtProperties properties = new JwtProperties("qua-ngan", Duration.ofHours(12));

        assertThat(validator.validate(properties)).anyMatch(violation -> violation
                .getPropertyPath()
                .toString()
                .equals("secret"));
    }

    @Test
    void jwtSecretDuDaiThiHopLe() {
        JwtProperties properties =
                new JwtProperties("mot-khoa-bi-mat-du-dai-toi-thieu-32-ky-tu", Duration.ofHours(12));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void jwtExpirationKhongDuongThiBiTuChoi() {
        // Token hết hạn ngay khi vừa cấp sẽ đẩy người dùng ra ngay sau khi đăng nhập.
        assertThatThrownBy(() -> new JwtProperties("mot-khoa-bi-mat-du-dai-toi-thieu-32-ky-tu", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_EXPIRATION");
    }

    @Test
    void storageThieuBucketThiBiTuChoi() {
        StorageProperties properties = new StorageProperties("http://localhost:9000", "key", "secret", "  ");

        assertThat(validator.validate(properties)).hasSize(1);
    }

    @Test
    void agentThieuBaseUrlThiBiTuChoi() {
        AgentServiceProperties properties = new AgentServiceProperties("", Duration.ofSeconds(120));

        assertThat(validator.validate(properties)).hasSize(1);
    }
}
