package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.service.account.GoogleIdentity;
import vn.hust.datn.evidence.testsupport.FakeGoogleIdTokenVerifier;
import vn.hust.datn.evidence.testsupport.RecordingAccountMailer;

/**
 * Các luồng tài khoản của V13 qua HTTP thật: xác thực email, quên mật khẩu, đổi mật khẩu, sửa hồ
 * sơ, Google. Thư đi vào {@link RecordingAccountMailer}; Google là {@link FakeGoogleIdTokenVerifier}.
 *
 * <p>Mỗi test dùng email ngẫu nhiên riêng: không có rollback giữa các test (xem
 * {@code AbstractIntegrationTest}), và hộp thư giả dùng chung cho cả bộ test.
 */
class AccountFlowTest extends AbstractWebIntegrationTest {

    private static final String PASSWORD = "mat-khau-du-dai-8";
    private static final String VERIFY = "Xác thực";
    private static final String RESET = "Đặt lại";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingAccountMailer mailer;

    @Autowired
    private FakeGoogleIdTokenVerifier google;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===================================== xác thực email =====================================

    @Test
    void chuaXacThucThiDangNhapDungMatKhauBi403() throws Exception {
        String email = register();

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/email-not-verified"));
    }

    @Test
    void chuaXacThucMaSaiMatKhauVanLa401NhuThuong() throws Exception {
        String email = register();

        // Không được lộ "tài khoản này tồn tại nhưng chưa xác thực" cho người KHÔNG biết mật khẩu.
        login(email, "mat-khau-sai-hoan-toan").andExpect(status().isUnauthorized());
    }

    @Test
    void linkXacThucDangNhapLuonVaChiDungDuocMotLan() throws Exception {
        String email = register();
        String token = mailer.latestToken(email, VERIFY).orElseThrow();

        verify(token).andExpect(status().isOk()).andExpect(jsonPath("$.user.emailVerified").value(true));
        login(email, PASSWORD).andExpect(status().isOk());

        verify(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/invalid-account-token"));
    }

    @Test
    void linkXacThucHetHanThi400() throws Exception {
        String email = register();
        String token = mailer.latestToken(email, VERIFY).orElseThrow();
        expireTokensOf(email);

        verify(token).andExpect(status().isBadRequest());
    }

    @Test
    void guiLaiNgayLapTucBiGianCachConQuaGianCachThiLinkCuHetHieuLuc() throws Exception {
        String email = register();
        String first = mailer.latestToken(email, VERIFY).orElseThrow();

        postJson("/api/v1/auth/resend-verification", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isNoContent());
        assertThat(mailer.sentTo(email)).hasSize(1);

        backdateTokensOf(email);
        postJson("/api/v1/auth/resend-verification", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isNoContent());
        String second = mailer.latestToken(email, VERIFY).orElseThrow();
        assertThat(second).isNotEqualTo(first);

        verify(first).andExpect(status().isBadRequest());
        verify(second).andExpect(status().isOk());
    }

    @Test
    void guiLaiVaQuenMatKhauVoiEmailKhongCoTaiKhoanVan204VaKhongGuiGi() throws Exception {
        String email = "khong-ton-tai-%s@test.local".formatted(UUID.randomUUID());

        postJson("/api/v1/auth/resend-verification", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/forgot-password", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isNoContent());

        assertThat(mailer.sentTo(email)).isEmpty();
    }

    @Test
    void tokenTrongDbLaHashKhongPhaiChuoiGoc() throws Exception {
        String email = register();
        String token = mailer.latestToken(email, VERIFY).orElseThrow();

        Integer plain = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_token WHERE token_hash = ?", Integer.class, token);
        assertThat(plain).isZero();
    }

    // ===================================== quên mật khẩu ======================================

    @Test
    void datLaiMatKhauDoiDuocMatKhauVaDangXuatMoiPhienCu() throws Exception {
        String email = register();
        JsonNode session = verifyAndRead(email);

        postJson("/api/v1/auth/forgot-password", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isNoContent());
        String reset = mailer.latestToken(email, RESET).orElseThrow();

        postJson("/api/v1/auth/reset-password", resetBody(reset, "mat-khau-moi-12345")).andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "mat-khau-moi-12345").andExpect(status().isOk());
        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        assertThat(mailer.sentTo(email)).anyMatch(m -> m.subject().contains("vừa được thay đổi"));

        postJson("/api/v1/auth/reset-password", resetBody(reset, "lan-thu-hai-12345")).andExpect(status().isBadRequest());
    }

    @Test
    void datLaiMatKhauCungXacThucLuonEmail() throws Exception {
        String email = register();

        postJson("/api/v1/auth/forgot-password", "{\"email\": \"%s\"}".formatted(email));
        String reset = mailer.latestToken(email, RESET).orElseThrow();
        postJson("/api/v1/auth/reset-password", resetBody(reset, "mat-khau-moi-12345")).andExpect(status().isNoContent());

        login(email, "mat-khau-moi-12345").andExpect(status().isOk());
    }

    @Test
    void tokenXacThucKhongDungDeDatLaiMatKhauDuoc() throws Exception {
        String email = register();
        String verifyToken = mailer.latestToken(email, VERIFY).orElseThrow();

        postJson("/api/v1/auth/reset-password", resetBody(verifyToken, "mat-khau-moi-12345"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Quy tắc mật khẩu của đăng ký phải áp cả ở hai đường đặt mật khẩu còn lại — nếu không, ai bị
     * chặn lúc đăng ký chỉ việc qua "quên mật khẩu" hoặc trang tài khoản để đặt mật khẩu yếu.
     */
    @Test
    void datLaiVaDoiMatKhauCungApQuyTacMatKhau() throws Exception {
        String email = register();
        JsonNode session = verifyAndRead(email);

        // Mật khẩu hiện tại dùng hằng PASSWORD, không viết chuỗi thẳng sau biến token: gitleaks
        // (generic-api-key) đọc `changePassword(token, "<chuỗi dài>"` là một API key bị lộ.
        changePassword(session.get("accessToken").asText(), PASSWORD, "khong-co-chu-so")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("Mật khẩu phải có ít nhất một chữ số"));

        postJson("/api/v1/auth/forgot-password", "{\"email\": \"%s\"}".formatted(email));
        String reset = mailer.latestToken(email, RESET).orElseThrow();
        postJson("/api/v1/auth/reset-password", resetBody(reset, "khong-co-chu-so"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("Mật khẩu phải có ít nhất một chữ số"));
        // Bị chặn ở bước validate thì link chưa bị tiêu: người dùng sửa mật khẩu rồi bấm lại được.
        postJson("/api/v1/auth/reset-password", resetBody(reset, "mat-khau-moi-12345")).andExpect(status().isNoContent());
    }

    // ===================================== hồ sơ / mật khẩu =====================================

    @Test
    void doiTenHienThi() throws Exception {
        String access = verifyAndRead(register()).get("accessToken").asText();

        getMockMvc()
                .perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"  Ten Moi  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ten Moi"));

        getMockMvc()
                .perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doiMatKhauSaiMatKhauHienTaiLa400KhongPhai401() throws Exception {
        String access = verifyAndRead(register()).get("accessToken").asText();

        // 401 sẽ khiến frontend tưởng hết phiên và tự đi refresh — phải là 400.
        changePassword(access, "sai-mat-khau-hien-tai", "mat-khau-moi-12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/wrong-current-password"));
    }

    @Test
    void doiMatKhauDungThiPhienCuBiDongVaNhanPhienMoi() throws Exception {
        String email = register();
        JsonNode session = verifyAndRead(email);

        changePassword(session.get("accessToken").asText(), PASSWORD, "mat-khau-moi-12345")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists());

        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        login(email, "mat-khau-moi-12345").andExpect(status().isOk());
    }

    // ===================================== Google =====================================

    @Test
    void cauHinhCongKhaiTraVeClientIdGoogle() throws Exception {
        getMockMvc()
                .perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleClientId").value("test-client-id.apps.googleusercontent.com"));
    }

    @Test
    void googleLanDauTaoTaiKhoanDaXacThucKhongMatKhau() throws Exception {
        String email = "google-moi-%s@test.local".formatted(UUID.randomUUID());
        String credential = google.register(new GoogleIdentity(
                "sub-" + UUID.randomUUID(), email, true, "Nguoi Dung Google", "https://lh3.googleusercontent.com/a/x"));

        JsonNode session = read(googleLogin(credential).andExpect(status().isOk()));
        assertThat(session.at("/user/emailVerified").asBoolean()).isTrue();
        assertThat(session.at("/user/hasPassword").asBoolean()).isFalse();
        assertThat(session.at("/user/googleLinked").asBoolean()).isTrue();
        assertThat(session.at("/user/displayName").asText()).isEqualTo("Nguoi Dung Google");
        assertThat(session.at("/user/avatarUrl").asText()).isEqualTo("https://lh3.googleusercontent.com/a/x");

        // Không có mật khẩu: đăng nhập bằng mật khẩu bất kỳ là 401, không phải lỗi 500.
        login(email, "bat-ky-mat-khau-nao").andExpect(status().isUnauthorized());

        // Đặt mật khẩu lần đầu: không có mật khẩu hiện tại để hỏi.
        changePassword(session.get("accessToken").asText(), null, "mat-khau-moi-12345").andExpect(status().isOk());
        login(email, "mat-khau-moi-12345").andExpect(status().isOk());
    }

    @Test
    void googleTrungEmailDaXacThucThiLienKetVaoCungTaiKhoan() throws Exception {
        String email = register();
        String userId = verifyAndRead(email).at("/user/id").asText();
        String credential = google.register(new GoogleIdentity("sub-" + UUID.randomUUID(), email, true, "X", null));

        googleLogin(credential)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.googleLinked").value(true));

        // Mật khẩu cũ vẫn dùng được: tài khoản đã xác thực là của chính chủ.
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void googleLienKetVaoTaiKhoanChuaXacThucThiXoaMatKhauDatTruoc() throws Exception {
        // Kẻ xấu đăng ký trước bằng email nạn nhân với mật khẩu của hắn, không xác thực được.
        String email = register();
        String credential = google.register(new GoogleIdentity("sub-" + UUID.randomUUID(), email, true, "Nan Nhan", null));

        googleLogin(credential).andExpect(status().isOk()).andExpect(jsonPath("$.user.hasPassword").value(false));

        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void googleChuaXacThucEmailThiTuChoi() throws Exception {
        String email = register();
        verifyAndRead(email);
        String credential = google.register(new GoogleIdentity("sub-" + UUID.randomUUID(), email, false, "X", null));

        googleLogin(credential)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/invalid-google-token"));
    }

    @Test
    void googleTokenGiaMaoThiTuChoi() throws Exception {
        googleLogin("token-khong-ai-cap").andExpect(status().isUnauthorized());
    }

    @Test
    void emailDaGanVoiGoogleKhacThiKhongGhiDe() throws Exception {
        String email = "google-hai-%s@test.local".formatted(UUID.randomUUID());
        googleLogin(google.register(new GoogleIdentity("sub-a-" + UUID.randomUUID(), email, true, "A", null)))
                .andExpect(status().isOk());

        googleLogin(google.register(new GoogleIdentity("sub-b-" + UUID.randomUUID(), email, true, "B", null)))
                .andExpect(status().isConflict());
    }

    // ===================================== helpers =====================================

    private String register() throws Exception {
        String email = "tai-khoan-%s@test.local".formatted(UUID.randomUUID());
        postJson(
                        "/api/v1/auth/register",
                        "{\"email\": \"%s\", \"password\": \"%s\", \"displayName\": \"Nguoi Dung\"}"
                                .formatted(email, PASSWORD))
                .andExpect(status().isCreated());
        return email;
    }

    private JsonNode verifyAndRead(String email) throws Exception {
        return read(verify(mailer.latestToken(email, VERIFY).orElseThrow()).andExpect(status().isOk()));
    }

    private ResultActions verify(String token) throws Exception {
        return postJson("/api/v1/auth/verify-email", "{\"token\": \"%s\"}".formatted(token));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", "{\"refreshToken\": \"%s\"}".formatted(refreshToken));
    }

    private ResultActions googleLogin(String credential) throws Exception {
        return postJson("/api/v1/auth/google", "{\"credential\": \"%s\"}".formatted(credential));
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        String body = current == null
                ? "{\"newPassword\": \"%s\"}".formatted(next)
                : "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next);
        return getMockMvc()
                .perform(post("/api/v1/auth/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static String resetBody(String token, String newPassword) {
        return "{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, newPassword);
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return getMockMvc().perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private void expireTokensOf(String email) {
        jdbcTemplate.update(
                "UPDATE account_token SET expires_at = now() - interval '1 minute' "
                        + "WHERE user_id = (SELECT id FROM app_user WHERE lower(email) = lower(?))",
                email);
    }

    /** Đẩy lùi thời điểm phát token để vượt qua giãn cách gửi lại, không phải chờ thật. */
    private void backdateTokensOf(String email) {
        jdbcTemplate.update(
                "UPDATE account_token SET created_at = created_at - interval '1 hour' "
                        + "WHERE user_id = (SELECT id FROM app_user WHERE lower(email) = lower(?))",
                email);
    }
}
