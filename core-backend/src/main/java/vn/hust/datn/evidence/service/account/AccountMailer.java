package vn.hust.datn.evidence.service.account;

/**
 * Cổng gửi email. Interface để test thay bằng bản ghi lại thư trong bộ nhớ — test KHÔNG BAO GIỜ
 * được gửi mail thật qua Gmail của người dùng.
 */
public interface AccountMailer {

    void send(OutgoingMail mail);
}
