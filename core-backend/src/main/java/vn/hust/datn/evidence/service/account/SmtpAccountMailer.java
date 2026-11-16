package vn.hust.datn.evidence.service.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.AccountProperties;

/**
 * Gửi email qua SMTP (Gmail + App Password, xem {@code infra/.env.example}).
 *
 * <p><b>Chưa cấu hình SMTP ({@code MAIL_USERNAME} rỗng) thì KHÔNG gửi, chỉ ghi nội dung thư vào
 * log ở mức WARN.</b> Không có lối này thì máy dev chưa điền Gmail sẽ không tạo nổi tài khoản nào
 * (đăng ký bắt buộc xác thực email). Đánh đổi: link xác thực/đặt lại mật khẩu nằm trong log — chấp
 * nhận được vì chỉ xảy ra khi người vận hành CHƯA bật mail, và log ghi rõ điều đó.
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
        String from = accountProperties.mailFrom() == null || accountProperties.mailFrom().isBlank()
                ? username
                : accountProperties.mailFrom();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        mailSender.send(message);
        log.info("Da gui email '{}' toi {}", mail.subject(), mail.to());
    }
}
