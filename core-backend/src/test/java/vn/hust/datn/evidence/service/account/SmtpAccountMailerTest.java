package vn.hust.datn.evidence.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import vn.hust.datn.evidence.config.properties.AccountProperties;

/** Tên người gửi: đặt được, có dấu vẫn đúng, và không bao giờ ra SMTP thật. */
class SmtpAccountMailerTest {

    private static final String GMAIL = "do-an@gmail.com";

    @Test
    void tenNguoiGuiCoDauDuocMaHoaVaDocLaiNguyenVen() throws Exception {
        CapturingSender sender = new CapturingSender();
        mailer(sender, null, "Nền tảng phân tích bằng chứng")
                .send(new OutgoingMail("a@test.local", "Xác thực email", "noi dung"));

        InternetAddress from = (InternetAddress) sender.sent.getFirst().getFrom()[0];
        assertThat(from.getAddress()).isEqualTo(GMAIL);
        assertThat(from.getPersonal()).isEqualTo("Nền tảng phân tích bằng chứng");
        // Header thô phải là dạng mã hoá RFC 2047 (=?UTF-8?...?=), không phải byte UTF-8 trần.
        assertThat(sender.sent.getFirst().getHeader("From")[0]).startsWith("=?UTF-8?");
    }

    @Test
    void mailFromDangTenVaDiaChiCungDuocNhan() throws Exception {
        CapturingSender sender = new CapturingSender();
        InternetAddress from = mailer(sender, "Evidence Agent <alias@gmail.com>", null).sender(GMAIL);

        assertThat(from.getAddress()).isEqualTo("alias@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("Evidence Agent");
    }

    @Test
    void mailFromNameThangTenTrongMailFrom() {
        InternetAddress from = mailer(new CapturingSender(), "Ten Cu <alias@gmail.com>", "Ten Moi").sender(GMAIL);

        assertThat(from.getPersonal()).isEqualTo("Ten Moi");
    }

    @Test
    void khongDatTenThiChiCoDiaChi() {
        InternetAddress from = mailer(new CapturingSender(), null, "  ").sender(GMAIL);

        assertThat(from.getAddress()).isEqualTo(GMAIL);
        assertThat(from.getPersonal()).isNull();
    }

    @Test
    void mailFromSaiDinhDangThiBaoLoiRo() {
        assertThatThrownBy(() -> mailer(new CapturingSender(), "khong phai email", null).sender(GMAIL))
                .isInstanceOf(MailPreparationException.class)
                .hasMessageContaining("MAIL_FROM");
    }

    private static SmtpAccountMailer mailer(CapturingSender sender, String mailFrom, String mailFromName) {
        MailProperties mail = new MailProperties();
        mail.setUsername(GMAIL);
        AccountProperties account = new AccountProperties(
                "http://app",
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofSeconds(60),
                mailFrom,
                mailFromName,
                false,
                null);
        return new SmtpAccountMailer(sender, mail, account);
    }

    /** JavaMailSenderImpl thật để dựng MimeMessage, nhưng giữ thư lại thay vì mở kết nối SMTP. */
    private static final class CapturingSender extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();

        @Override
        public void send(MimeMessage message) {
            try {
                message.saveChanges();
            } catch (MessagingException e) {
                throw new IllegalStateException(e);
            }
            sent.add(message);
        }
    }
}
