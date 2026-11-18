package vn.hust.datn.evidence.service.account;

/**
 * Một email gửi đi, mang HAI bản của cùng một nội dung: văn bản thuần và HTML.
 *
 * <p>Trước 18/11/2026 thư chỉ có văn bản thuần, với lý do "không HTML thì không có gì để chèn mã".
 * Nay có HTML (người dùng chọn, để thư gửi từ {@code vsleuth.id.vn} trông chuyên nghiệp), và lý
 * do cũ vẫn được giữ bằng cách khác: cả hai bản sinh từ CÙNG một {@link EmailContent} qua
 * {@link EmailTemplate}, nơi DUY NHẤT dựng HTML và nơi escape mọi chuỗi — kể cả tên hiển thị do
 * người dùng tự đặt. Không chỗ nào khác được tự ghép chuỗi HTML.
 *
 * @param text bản văn bản thuần — trình mail không hiển thị HTML, và bản để test đọc token
 * @param html bản HTML; {@code null} thì chỉ gửi văn bản thuần
 */
public record OutgoingMail(String to, String subject, String text, String html) {}
