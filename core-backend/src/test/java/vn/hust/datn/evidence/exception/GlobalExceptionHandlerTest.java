package vn.hust.datn.evidence.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.probe.ExceptionHandlerProbeController;

/**
 * Trước tuần 4, ngoại lệ đầu vào cơ bản (UUID sai định dạng, JSON hỏng) rơi vào catch-all
 * {@code Exception} và trả về 500 — sai ngữ nghĩa cho lỗi rõ ràng của client. Test này xác nhận
 * ba handler mới trả đúng 400, không lẫn vào lưới an toàn cuối cùng.
 *
 * <p>{@link ExceptionHandlerProbeController} nằm ngoài package được {@code @SpringBootApplication}
 * quét (để không rò vào spec OpenAPI công khai) nên phải nạp tường minh bằng {@code @Import} ở
 * đây. Đánh đổi: thêm một Spring test context (một lần khởi động + Flyway riêng) — chấp nhận được
 * để giữ spec sạch.
 */
@Import(ExceptionHandlerProbeController.class)
class GlobalExceptionHandlerTest extends AbstractWebIntegrationTest {

    @Test
    void uuidSaiDinhDangTraVe400KhongPhai500() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/_probe/by-id/khong-phai-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/invalid-parameter"));
    }

    @Test
    void jsonHongCuPhapTraVe400() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/_probe/echo").contentType(MediaType.APPLICATION_JSON).content("{khong-phai-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/malformed-request-body"));
    }

    @Test
    void requestParamViPhamRangBuocTraVe400CoFieldErrors() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/_probe/validated").param("value", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/validation-failed"))
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void requestParamHopLeThiTraVe200() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/_probe/validated").param("value", "abcdef"))
                .andExpect(status().isOk());
    }
}
