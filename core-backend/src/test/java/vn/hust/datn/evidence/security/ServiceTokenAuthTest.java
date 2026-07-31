package vn.hust.datn.evidence.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;

/** Kiểm chứng riêng {@code ServiceTokenAuthenticationFilter} — tách khỏi {@link SecurityFilterChainTest}. */
class ServiceTokenAuthTest extends AbstractWebIntegrationTest {

    @Test
    void serviceTokenDungThiGoiDuocTools() throws Exception {
        // Body rỗng -> 400 validation-failed, nhưng quan trọng là KHÔNG PHẢI 401/403: request đã
        // qua được cửa xác thực, chỉ hỏng ở bước đọc body - đúng thứ test này cần chứng minh.
        getMockMvc()
                .perform(post("/api/v1/tools/get_warning").with(serviceToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceTokenSaiTraVe401() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/tools/search_evidence").header("X-Service-Token", "token-hoan-toan-sai-nhung-du-dai-32-ky-tu"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thieuHeaderTraVe401() throws Exception {
        getMockMvc().perform(get("/api/v1/tools/search_evidence")).andExpect(status().isUnauthorized());
    }

    @Test
    void serviceTokenKhongGoiDuocApiNguoiDung() throws Exception {
        // X-Service-Token không phải chìa khoá vạn năng - toolChain chỉ khớp /api/v1/tools/**,
        // request tới /api/v1/projects đi qua apiChain (yêu cầu JWT), không đọc header này.
        getMockMvc().perform(get("/api/v1/projects").with(serviceToken())).andExpect(status().isUnauthorized());
    }
}
