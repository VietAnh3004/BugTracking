package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Toàn bộ request kèm {@code .with(authenticatedAs(user))} — controller giờ đọc
 * {@code @AuthenticationPrincipal CurrentUser} thật (không phải {@code Jwt} trần của
 * {@code .with(jwt())}) để forward xuống {@code ProjectService} làm {@code ownerId}.
 */
class ProjectControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Test
    void taoDuAnThanhCongTraVe201VoiLocationHeader() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        String body = """
                {"name": "vulnshop-%d", "description": "case study"}
                """.formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authenticatedAs(user)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.startsWith("vulnshop-")));
    }

    @Test
    void taoDuAnThieuTenTraVe400() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authenticatedAs(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/validation-failed"));
    }

    @Test
    void layDuAnKhongTonTaiTraVe404() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(get("/api/v1/projects/" + java.util.UUID.randomUUID()).with(authenticatedAs(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));
    }

    @Test
    void danhSachTraVePageResponse() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(get("/api/v1/projects").with(authenticatedAs(user)))
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
