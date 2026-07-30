package vn.hust.datn.evidence;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;
}
