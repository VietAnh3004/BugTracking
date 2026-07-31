package vn.hust.datn.evidence.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;

/**
 * Toàn bộ request kèm {@code .with(jwt())} — chỉ cần "đã xác thực", chưa cần đúng
 * {@code CurrentUser}/quyền sở hữu (đó là B6, tuần 5-7). Thiếu nó thì mọi request rơi vào 401 của
 * {@code apiChain} ({@link vn.hust.datn.evidence.config.SecurityConfig}) trước khi chạm tới
 * controller.
 */
class ProjectControllerTest extends AbstractWebIntegrationTest {

    @Test
    void taoDuAnThanhCongTraVe201VoiLocationHeader() throws Exception {
        String body = """
                {"name": "vulnshop-%d", "description": "case study"}
                """.formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.startsWith("vulnshop-")));
    }

    @Test
    void taoDuAnThieuTenTraVe400() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{}").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/validation-failed"));
    }

    @Test
    void layDuAnKhongTonTaiTraVe404() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects/" + java.util.UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));
    }

    @Test
    void danhSachTraVePageResponse() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void khongCoTokenTraVe401() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/unauthenticated"));
    }
}
