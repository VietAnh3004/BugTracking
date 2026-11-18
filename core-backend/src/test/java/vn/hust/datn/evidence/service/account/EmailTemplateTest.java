package vn.hust.datn.evidence.service.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EmailTemplateTest {

    private static final String BRAND = "VSleuth";
    private static final String SITE = "https://vsleuth.id.vn";
    private static final String LINK = "https://vsleuth.id.vn/verify-email#token=abc_DEF-123";

    /**
     * Cạm bẫy chính của email HTML: tên hiển thị do NGƯỜI DÙNG tự đặt. Không escape thì một người
     * đặt tên là một thẻ {@code <a>} sẽ chèn được link lừa đảo vào thư mang tên miền đã ký DKIM
     * của hệ thống — thư trông hoàn toàn chính chủ.
     */
    @Test
    void tenNguoiDungChuaHtmlBiVoHieuHoa() {
        String attack = "<a href=\"https://lua-dao.example\">Bấm vào đây</a><script>alert(1)</script>";

        String html = EmailTemplate.html(content(attack, LINK), BRAND, SITE);

        assertThat(html).doesNotContain("<script>").doesNotContain("href=\"https://lua-dao.example\"");
        assertThat(html).contains("&lt;script&gt;").contains("&lt;a href=&quot;https://lua-dao.example&quot;&gt;");
    }

    /** Escape chỉ đổi ký tự đặc biệt của HTML — chữ tiếng Việt phải giữ nguyên, không thành &#7845;… */
    @Test
    void chuTiengVietGiuNguyenKhongThanhThucTheSo() {
        String html = EmailTemplate.html(content("Nguyễn Việt Anh", LINK), BRAND, SITE);

        assertThat(html).contains("Chào Nguyễn Việt Anh,").contains("Xác thực địa chỉ email của bạn");
    }

    /**
     * Nút bấm và link dự phòng phải trỏ cùng một URL. {@code &} trong URL phải thành {@code &amp;}
     * trong thuộc tính href — trình mail giải lại về {@code &} khi bấm.
     */
    @Test
    void nutBamVaLinkDuPhongCungTroMotUrlDaEscape() {
        String withQuery = "https://vsleuth.id.vn/x?a=1&b=2";

        String html = EmailTemplate.html(content("A", withQuery), BRAND, SITE);

        assertThat(html.split("href=\"https://vsleuth.id.vn/x\\?a=1&amp;b=2\"", -1)).hasSize(3);
        assertThat(html).doesNotContain("a=1&b=2");
    }

    /** Bản văn bản thuần là bản test đọc token, và là bản người dùng thấy khi HTML bị chặn. */
    @Test
    void banVanBanThuanCoDuNoiDungVaLinkNguyenVan() {
        String text = EmailTemplate.text(content("Nguyễn Việt Anh", LINK), BRAND, SITE);

        assertThat(text)
                .startsWith("Chào Nguyễn Việt Anh,")
                .contains("Đoạn mở đầu.")
                .contains("Xác thực email:\n" + LINK)
                .contains("Đoạn kết.")
                .contains("VSleuth · https://vsleuth.id.vn")
                .doesNotContain("<");
    }

    @Test
    void khongCoTenThiChaoChung() {
        assertThat(EmailTemplate.text(content(null, LINK), BRAND, SITE)).startsWith("Chào bạn,");
        assertThat(EmailTemplate.text(content("   ", LINK), BRAND, SITE)).startsWith("Chào bạn,");
    }

    /** Ký tự % trong nội dung không được làm hỏng template (thân thư đi vào formatted() như THAM SỐ). */
    @Test
    void dauPhanTramTrongNoiDungKhongLamHongTemplate() {
        String html = EmailTemplate.html(content("100%s %d", "https://vsleuth.id.vn/a%20b"), BRAND, SITE);

        assertThat(html).contains("Chào 100%s %d,").contains("https://vsleuth.id.vn/a%20b");
    }

    @Test
    void chanThuHienTenMienKhongKemGiaoThuc() {
        assertThat(EmailTemplate.displayHost("https://vsleuth.id.vn/")).isEqualTo("vsleuth.id.vn");
        assertThat(EmailTemplate.displayHost("khong phai url")).isEqualTo("khong phai url");
    }

    private static EmailContent content(String name, String url) {
        return new EmailContent(
                "Xác thực email tài khoản VSleuth",
                "Xác thực địa chỉ email của bạn",
                name,
                List.of("Đoạn mở đầu."),
                new EmailContent.Action("Xác thực email", url),
                List.of("Đoạn kết."));
    }
}
