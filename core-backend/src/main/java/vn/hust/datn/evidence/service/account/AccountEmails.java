package vn.hust.datn.evidence.service.account;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.AccountProperties;
import vn.hust.datn.evidence.domain.AccountTokenPurpose;
import vn.hust.datn.evidence.domain.User;

/**
 * Nội dung các email tài khoản. Lớp này chỉ nói thư NÓI GÌ; thư TRÔNG thế nào (HTML, văn bản
 * thuần, escape) là việc của {@link EmailTemplate}.
 *
 * <p>Token đặt ở FRAGMENT ({@code #token=...}) chứ không ở query string: trình duyệt không bao giờ
 * gửi fragment lên máy chủ, nên token không nằm trong access log của nginx hay của proxy nào trên
 * đường đi, và không lọt ra ngoài qua header {@code Referer}.
 */
@Component
public class AccountEmails {

    private final AccountTokenService tokens;
    private final MailDispatcher dispatcher;
    private final AccountProperties properties;

    public AccountEmails(AccountTokenService tokens, MailDispatcher dispatcher, AccountProperties properties) {
        this.tokens = tokens;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    /** @return {@code false} khi bị giãn cách (vừa gửi một thư cùng loại) nên không gửi */
    public boolean sendVerification(User user) {
        return tokens.issue(user.getId(), AccountTokenPurpose.VERIFY_EMAIL)
                .map(token -> {
                    send(user, new EmailContent(
                            "Xác thực email tài khoản " + properties.brandName(),
                            "Xác thực địa chỉ email của bạn",
                            user.getDisplayName(),
                            List.of(
                                    "Bạn vừa đăng ký tài khoản trên nền tảng phân tích chương trình dựa trên bằng chứng.",
                                    "Bấm nút dưới đây để xác thực email. Liên kết có hiệu lực trong "
                                            + describe(tokens.ttlOf(AccountTokenPurpose.VERIFY_EMAIL)) + "."),
                            new EmailContent.Action("Xác thực email", link("/verify-email#token=" + token)),
                            List.of("Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email.")));
                    return true;
                })
                .orElse(false);
    }

    public boolean sendPasswordReset(User user) {
        return tokens.issue(user.getId(), AccountTokenPurpose.RESET_PASSWORD)
                .map(token -> {
                    send(user, new EmailContent(
                            "Đặt lại mật khẩu " + properties.brandName(),
                            "Đặt lại mật khẩu",
                            user.getDisplayName(),
                            List.of(
                                    "Có yêu cầu đặt lại mật khẩu cho tài khoản gắn với email này.",
                                    "Bấm nút dưới đây để đặt mật khẩu mới. Liên kết có hiệu lực trong "
                                            + describe(tokens.ttlOf(AccountTokenPurpose.RESET_PASSWORD))
                                            + " và chỉ dùng được một lần."),
                            new EmailContent.Action("Đặt mật khẩu mới", link("/reset-password#token=" + token)),
                            List.of("Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu hiện tại vẫn giữ nguyên.")));
                    return true;
                })
                .orElse(false);
    }

    /**
     * Báo mật khẩu vừa đổi. Chủ tài khoản thật là người duy nhất phát hiện được một lần đổi mật khẩu
     * KHÔNG phải do họ làm — không có thư này thì kẻ chiếm tài khoản đổi xong là xong.
     */
    public void sendPasswordChanged(User user) {
        send(user, new EmailContent(
                "Mật khẩu tài khoản " + properties.brandName() + " vừa được thay đổi",
                "Mật khẩu của bạn vừa được thay đổi",
                user.getDisplayName(),
                List.of(
                        "Mật khẩu tài khoản của bạn vừa được thay đổi, và mọi phiên đăng nhập cũ đã bị đăng xuất.",
                        "Nếu không phải bạn, hãy lấy lại tài khoản ngay bằng chức năng \"Quên mật khẩu\"."),
                new EmailContent.Action("Lấy lại tài khoản", link("/forgot-password")),
                List.of("Nếu chính bạn vừa đổi mật khẩu, không cần làm gì thêm.")));
    }

    private void send(User user, EmailContent content) {
        String brand = properties.brandName();
        String site = properties.publicUrlWithoutTrailingSlash();
        dispatcher.sendAfterCommit(new OutgoingMail(
                user.getEmail(),
                content.subject(),
                EmailTemplate.text(content, brand, site),
                EmailTemplate.html(content, brand, site)));
    }

    private String link(String path) {
        return properties.publicUrlWithoutTrailingSlash() + path;
    }

    static String describe(Duration ttl) {
        if (ttl.toMinutes() < 60) {
            return ttl.toMinutes() + " phút";
        }
        return ttl.toHours() + " giờ";
    }
}
