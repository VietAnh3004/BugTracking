package vn.hust.datn.evidence.service.account;

import java.time.Duration;

import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.AccountProperties;
import vn.hust.datn.evidence.domain.AccountTokenPurpose;
import vn.hust.datn.evidence.domain.User;

/**
 * Nội dung các email tài khoản.
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
                    String link = properties.publicUrlWithoutTrailingSlash() + "/verify-email#token=" + token;
                    dispatcher.sendAfterCommit(new OutgoingMail(
                            user.getEmail(),
                            "Xác thực email tài khoản",
                            """
                            Chào %s,

                            Bạn vừa đăng ký tài khoản trên nền tảng phân tích chương trình dựa trên bằng chứng.
                            Mở liên kết dưới đây để xác thực email (hiệu lực %s):

                            %s

                            Nếu bạn không đăng ký, hãy bỏ qua email này.
                            """
                                    .formatted(user.getDisplayName(), describe(tokens.ttlOf(AccountTokenPurpose.VERIFY_EMAIL)), link)));
                    return true;
                })
                .orElse(false);
    }

    public boolean sendPasswordReset(User user) {
        return tokens.issue(user.getId(), AccountTokenPurpose.RESET_PASSWORD)
                .map(token -> {
                    String link = properties.publicUrlWithoutTrailingSlash() + "/reset-password#token=" + token;
                    dispatcher.sendAfterCommit(new OutgoingMail(
                            user.getEmail(),
                            "Đặt lại mật khẩu",
                            """
                            Chào %s,

                            Có yêu cầu đặt lại mật khẩu cho tài khoản gắn với email này.
                            Mở liên kết dưới đây để đặt mật khẩu mới (hiệu lực %s, chỉ dùng được một lần):

                            %s

                            Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu hiện tại vẫn giữ nguyên.
                            """
                                    .formatted(user.getDisplayName(), describe(tokens.ttlOf(AccountTokenPurpose.RESET_PASSWORD)), link)));
                    return true;
                })
                .orElse(false);
    }

    /**
     * Báo mật khẩu vừa đổi. Chủ tài khoản thật là người duy nhất phát hiện được một lần đổi mật khẩu
     * KHÔNG phải do họ làm — không có thư này thì kẻ chiếm tài khoản đổi xong là xong.
     */
    public void sendPasswordChanged(User user) {
        dispatcher.sendAfterCommit(new OutgoingMail(
                user.getEmail(),
                "Mật khẩu tài khoản vừa được thay đổi",
                """
                Chào %s,

                Mật khẩu tài khoản của bạn vừa được thay đổi, và mọi phiên đăng nhập cũ đã bị đăng xuất.

                Nếu không phải bạn, hãy dùng chức năng "Quên mật khẩu" ngay để lấy lại tài khoản:
                %s/forgot-password
                """
                        .formatted(user.getDisplayName(), properties.publicUrlWithoutTrailingSlash())));
    }

    static String describe(Duration ttl) {
        if (ttl.toMinutes() < 60) {
            return ttl.toMinutes() + " phút";
        }
        return ttl.toHours() + " giờ";
    }
}
