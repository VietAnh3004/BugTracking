package vn.hust.datn.evidence.service.account;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.hust.datn.evidence.config.properties.AccountProperties;

/**
 * Gửi email SAU KHI transaction commit, trên luồng riêng.
 *
 * <ul>
 *   <li><b>Sau commit:</b> gửi trong transaction mà transaction rollback thì người dùng nhận một
 *       link trỏ tới token không tồn tại.
 *   <li><b>Luồng riêng:</b> SMTP của Gmail mất 1-3 giây. Gửi đồng bộ thì "quên mật khẩu" với email
 *       CÓ tài khoản chậm hơn hẳn email KHÔNG có — kẻ dò tài khoản đo thời gian phản hồi là biết,
 *       dù thông điệp trả về giống hệt nhau.
 * </ul>
 *
 * <p>Gửi hỏng chỉ ghi log: request đã trả về cho người dùng, không còn ai để báo lỗi. Người dùng
 * thấy không có thư thì bấm "Gửi lại".
 *
 * <p>Constructor viết tay vì {@code @Qualifier} — Lombok không chép nó sang tham số (CLAUDE.md).
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final AccountMailer mailer;
    private final Executor mailExecutor;
    private final AccountProperties properties;

    public MailDispatcher(
            AccountMailer mailer, @Qualifier("mailExecutor") Executor mailExecutor, AccountProperties properties) {
        this.mailer = mailer;
        this.mailExecutor = mailExecutor;
        this.properties = properties;
    }

    public void sendAfterCommit(OutgoingMail mail) {
        Runnable send = () -> {
            try {
                mailer.send(mail);
            } catch (RuntimeException e) {
                log.error("Gui email '{}' toi {} that bai: {}", mail.subject(), mail.to(), e.toString());
            }
        };
        Runnable dispatch = properties.mailAsync() ? () -> mailExecutor.execute(send) : send;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
