package vn.hust.datn.evidence.testsupport;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.hust.datn.evidence.service.account.AccountMailer;
import vn.hust.datn.evidence.service.account.OutgoingMail;

/** Giữ thư trong bộ nhớ thay vì gửi SMTP — test không bao giờ được gửi mail thật. */
public class RecordingAccountMailer implements AccountMailer {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(OutgoingMail mail) {
        sent.add(mail);
    }

    public List<OutgoingMail> sentTo(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** Token trong thư MỚI NHẤT gửi tới {@code email} có tiêu đề chứa {@code subjectPart}. */
    public Optional<String> latestToken(String email, String subjectPart) {
        List<OutgoingMail> mails = sentTo(email).stream()
                .filter(m -> m.subject().contains(subjectPart))
                .toList();
        if (mails.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN.matcher(mails.getLast().body());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
