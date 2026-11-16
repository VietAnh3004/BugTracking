package vn.hust.datn.evidence.service.account;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.AccountProperties;

/**
 * Gửi email qua SMTP (Gmail + App Password, xem {@code infra/.env.example}).
 *
 * <p><b>Chưa cấu hình SMTP ({@code MAIL_USERNAME} rỗng) thì KHÔNG gửi, chỉ ghi nội dung thư vào
 * log ở mức WARN.</b> Không có lối này thì máy dev chưa điền Gmail sẽ không tạo nổi tài khoản nào
 * (đăng ký bắt buộc xác thực email). Đánh đổi: link xác thực/đặt lại mật khẩu nằm trong log — chấp
 * nhận được vì chỉ xảy ra khi người vận hành CHƯA bật mail, và log ghi rõ điều đó.
 *
 * <p><b>Tên người gửi</b> (phần "Tên" trong {@code Tên <địa-chỉ>}) đi qua
 * {@link InternetAddress#InternetAddress(String, String, String)} với UTF-8: tên có dấu tiếng Việt
 * được mã hoá theo RFC 2047. Ghép chuỗi {@code "Tên <addr>"} rồi để thư viện tự đoán thì tên có dấu
 * hiện thành ký tự rác ở một số trình đọc thư.
 */
@Component
public class SmtpAccountMailer implements AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpAccountMailer.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final AccountProperties accountProperties;

    public SmtpAccountMailer(
            JavaMailSender mailSender, MailProperties mailProperties, AccountProperties accountProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.accountProperties = accountProperties;
    }

    @Override
    public void send(OutgoingMail mail) {
        String username = mailProperties.getUsername();
        if (username == null || username.isBlank()) {
            log.warn(
                    "SMTP CHUA CAU HINH (MAIL_USERNAME rong) - email KHONG duoc gui. Den: {} | Tieu de: {}\n{}",
                    mail.to(),
                    mail.subject(),
                    mail.body());
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(sender(username));
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.body(), false);
        } catch (MessagingException e) {
            throw new MailPreparationException("Khong dung duoc email '" + mail.subject() + "'", e);
        }
        mailSender.send(message);
        log.info("Da gui email '{}' toi {}", mail.subject(), mail.to());
    }

    /**
     * Người gửi = địa chỉ ({@code MAIL_FROM}, trống thì {@code MAIL_USERNAME}) + tên hiển thị
     * ({@code MAIL_FROM_NAME}). {@code MAIL_FROM} cũng nhận sẵn dạng {@code Tên <địa-chỉ>}; khi đó
     * {@code MAIL_FROM_NAME} (nếu có) thắng.
     */
    InternetAddress sender(String username) {
        String configured = accountProperties.mailFrom();
        try {
            InternetAddress base = configured == null || configured.isBlank()
                    ? new InternetAddress(username, true)
                    : new InternetAddress(configured, true);
            String name = accountProperties.mailFromName();
            String personal = name != null && !name.isBlank() ? name.trim() : base.getPersonal();
            return personal == null || personal.isBlank()
                    ? new InternetAddress(base.getAddress())
                    : new InternetAddress(base.getAddress(), personal, StandardCharsets.UTF_8.name());
        } catch (AddressException | UnsupportedEncodingException e) {
            throw new MailPreparationException("MAIL_FROM khong phai dia chi email hop le", e);
        }
    }
}
