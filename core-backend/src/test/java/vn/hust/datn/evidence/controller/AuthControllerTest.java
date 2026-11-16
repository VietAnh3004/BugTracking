package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.testsupport.RecordingAccountMailer;

/**
 * 3 endpoint auth qua request thật (không gọi {@code AuthService} trực tiếp) — kiểm cả tầng JSON,
 * mã trạng thái, và hai tính chất bảo mật khó thấy qua unit test: chống dò tài khoản (thông điệp
 * lỗi giống nhau) và không rò {@code password_hash} ra response.
 */
class AuthControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingAccountMailer mailer;

    @Test
    void dangKyThanhCongTraVe201KhongKemTokenVaGuiThuXacThuc() throws Exception {
        String email = "user-%d@test.local".formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "mat-khau-du-dai-8", "Nguyen Van A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                // Chưa xác thực email thì chưa có phiên: KHÔNG được có token nào trong response.
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        assertThat(mailer.latestToken(email, "Xác thực")).isPresent();
    }

    @Test
    void dangKyTrungEmailTraVe409() throws Exception {
        String email = "trung-%d@test.local".formatted(System.nanoTime());
        String body = registerBody(email, "mat-khau-du-dai-8", "Nguoi Dung");

        getMockMvc().perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));

        getMockMvc()
                .perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/email-already-used"));
    }

    @Test
    void dangKyEmailKhacHoaTrungThuongVanBiCoiLaTrung() throws Exception {
        String base = "hoa-thuong-%d@test.local".formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(base.toLowerCase(), "mat-khau-du-dai-8", "A")));

        getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(base.toUpperCase(), "mat-khau-du-dai-8", "B")))
                .andExpect(status().isConflict());
    }

    @Test
    void matKhauQuaNganTraVe400KemFieldErrors() throws Exception {
        String email = "yeu-%d@test.local".formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "ngan", "Nguoi Dung")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/validation-failed"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void dangNhapSaiEmailVaSaiMatKhauTraVeCungMotThongDiep() throws Exception {
        String email = "dang-nhap-%d@test.local".formatted(System.nanoTime());
        getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "mat-khau-dung-8", "Nguoi Dung")));

        String detailKhiSaiMatKhau = extractDetail(getMockMvc()
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "mat-khau-sai")))
                .andExpect(status().isUnauthorized())
                .andReturn());

        String detailKhiEmailKhongTonTai = extractDetail(getMockMvc()
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("khong-ton-tai-%d@test.local".formatted(System.nanoTime()), "bat-ky")))
                .andExpect(status().isUnauthorized())
                .andReturn());

        assertThat(detailKhiSaiMatKhau).isEqualTo(detailKhiEmailKhongTonTai);
    }

    @Test
    void khongResponseNaoChuaPasswordHash() throws Exception {
        String email = "khong-lo-hash-%d@test.local".formatted(System.nanoTime());

        MvcResult result = getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "mat-khau-du-dai-8", "Nguoi Dung")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("passwordHash");
    }

    @Test
    void meTraVeDungNguoiDungCuaToken() throws Exception {
        String email = "me-%d@test.local".formatted(System.nanoTime());

        getMockMvc()
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "mat-khau-du-dai-8", "Chinh Chu")))
                .andExpect(status().isCreated());

        MvcResult verifyResult = getMockMvc()
                .perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(mailer.latestToken(email, "Xác thực").orElseThrow())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();

        getMockMvc()
                .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Chinh Chu"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.googleLinked").value(false))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("https://gravatar.com/avatar/")));
    }

    private String extractDetail(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("title").asText();
    }

    private String registerBody(String email, String password, String displayName) {
        return """
                {"email": "%s", "password": "%s", "displayName": "%s"}
                """
                .formatted(email, password, displayName);
    }

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """
                .formatted(email, password);
    }
}
