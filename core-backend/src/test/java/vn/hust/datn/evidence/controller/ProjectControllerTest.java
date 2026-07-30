package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;

class ProjectControllerTest extends AbstractWebIntegrationTest {

    @Test
    void taoDuAnThanhCongTraVe201VoiLocationHeader() throws Exception {
        String body = """
                {"name": "vulnshop-%d", "description": "case study"}
                """.formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.startsWith("vulnshop-")));
    }

    @Test
    void taoDuAnThieuTenTraVe400() throws Exception {
        getMockMvc()
                .perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/validation-failed"));
    }

    @Test
    void layDuAnKhongTonTaiTraVe404() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));
    }

    @Test
    void danhSachTraVePageResponse() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").exists());
    }
}
