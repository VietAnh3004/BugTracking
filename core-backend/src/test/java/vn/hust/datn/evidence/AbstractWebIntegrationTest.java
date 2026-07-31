package vn.hust.datn.evidence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import lombok.Getter;

/**
 * Base cho mọi test đụng tới controller.
 *
 * <p>Đặt {@code @AutoConfigureMockMvc} ở đúng MỘT chỗ, không rải trên từng class test — annotation
 * này góp phần vào cache key của Spring test context, rải lên nhiều class sẽ tạo nhiều context
 * (nhiều lần khởi động + chạy lại Flyway) thay vì tái dùng một context chung.
 *
 * <p>Dùng MockMvc (webEnvironment mặc định = MOCK), không {@code TestRestTemplate}/{@code
 * RANDOM_PORT}: chỉ ở chế độ MOCK thì {@code @Transactional} trên test mới rollback được (request
 * chạy cùng thread, cùng transaction với test). Với RANDOM_PORT, request chạy ở thread khác nên
 * không rollback theo transaction của test — phải tự dọn DB giữa các test, và với
 * {@code UNIQUE (analysis_run_id, seq_in_run)} thì dữ liệu rác sót lại sẽ gây fail ngẫu nhiên.
 */
@AutoConfigureMockMvc
@Getter
public abstract class AbstractWebIntegrationTest extends AbstractIntegrationTest {

    /** Khớp {@code app.security.service-token} trong {@code application-test.yml}. */
    protected static final String TEST_SERVICE_TOKEN = "test-only-service-token-du-32-ky-tu-khong-dung-that";

    @Autowired
    private MockMvc mockMvc;

    /** Header service token hợp lệ cho request tới {@code /api/v1/tools/**} trong test. */
    protected static RequestPostProcessor serviceToken() {
        return request -> {
            request.addHeader("X-Service-Token", TEST_SERVICE_TOKEN);
            return request;
        };
    }
}
