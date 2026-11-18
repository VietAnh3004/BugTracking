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
 * Gửi email qua SMTP. Trên VPS là Resend, gửi từ {@code no-reply@vsleuth.id.vn} (docs/email.md);
 * trên máy dev có thể là Gmail + App Password (xem {@code infra/.env.example}). Cả hai đều là SMTP
 * chuẩn nên lớp này không biết đang nói chuyện với ai — đổi nhà cung cấp chỉ là đổi biến môi trường.
 *
 * <p><b>Mỗi thư gửi hai bản</b> trong một {@code multipart/alternative}: văn bản thuần và HTML.
 * Trình mail tự chọn bản nó hiển thị được; thiếu bản văn bản thuần thì một số bộ lọc spam chấm
 * điểm xấu hơn.
 *
 * <p><b>Cấu hình sai thì dừng LÚC KHỞI ĐỘNG, không phải lúc gửi.</b> Thư gửi trên luồng nền sau
 * commit ({@link MailDispatcher}), nên lỗi lúc gửi chỉ thành một dòng log mà không ai đọc —
 * người dùng đăng ký xong và chờ một email không bao giờ tới. Vì vậy constructor dựng thử địa chỉ
 * người gửi ngay, và ném nếu nó không hợp lệ.
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
        String username = mailProperties.getUsername();
        if (username != null && !username.isBlank()) {
            sender(username); // fail-fast: xem Javadoc của lớp
        }
    }

    @Override
    public void send(OutgoingMail mail) {
        String username = mailProperties.getUsername();
        if (username == null || username.isBlank()) {
            log.warn(
                    "SMTP CHUA CAU HINH (MAIL_USERNAME rong) - email KHONG duoc gui. Den: {} | Tieu de: {}\n{}",
                    mail.to(),
                    mail.subject(),
                    mail.text());
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            boolean hasHtml = mail.html() != null && !mail.html().isBlank();
            MimeMessageHelper helper = new MimeMessageHelper(message, hasHtml, StandardCharsets.UTF_8.name());
            helper.setFrom(sender(username));
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            if (hasHtml) {
                helper.setText(mail.text(), mail.html()); // multipart/alternative: văn bản thuần + HTML
            } else {
                helper.setText(mail.text(), false);
            }
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
     *
     * <p>Lùi về {@code MAIL_USERNAME} chỉ đúng với Gmail, nơi tên đăng nhập CHÍNH LÀ địa chỉ email.
     * Resend (và phần lớn dịch vụ gửi thư) dùng tên đăng nhập kiểu {@code resend} — không phải địa
     * chỉ nào cả. Khi đó phải có {@code MAIL_FROM}, và thiếu thì báo đúng tên biến cần đặt thay vì
     * một lỗi cú pháp địa chỉ khó hiểu.
     */
    InternetAddress sender(String username) {
        String configured = accountProperties.mailFrom();
        if ((configured == null || configured.isBlank()) && !username.contains("@")) {
            throw new MailPreparationException(("MAIL_USERNAME '%s' khong phai dia chi email (Resend dung 'resend')"
                            + " - phai dat MAIL_FROM, vd MAIL_FROM=no-reply@<ten mien da xac minh>")
                    .formatted(username));
        }
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
