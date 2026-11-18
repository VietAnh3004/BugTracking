package vn.hust.datn.evidence.service.account;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * Nơi DUY NHẤT dựng thân email — cả bản văn bản thuần lẫn bản HTML, từ cùng một {@link EmailContent}.
 *
 * <h2>Mọi chuỗi đi vào HTML đều qua {@link #esc}</h2>
 *
 * {@code recipientName} là tên hiển thị người dùng TỰ ĐẶT. Không escape thì một người đặt tên
 * {@code <a href="https://lua-dao">Bấm vào đây</a>} sẽ chèn được link vào một email mang thương
 * hiệu và tên miền của hệ thống — đúng loại thư lừa đảo mà xác thực DKIM làm cho trông đáng tin.
 * Escape cả chuỗi do mình viết: rẻ, và không phải nhớ chỗ nào "chắc chắn an toàn".
 *
 * <h2>Vì sao HTML trông "cổ"</h2>
 *
 * Bảng lồng nhau và style viết thẳng vào thẻ (inline), không {@code <style>}, không flexbox: Gmail
 * bỏ phần lớn {@code <style>} trong {@code <head>}, Outlook trên Windows dựng HTML bằng engine của
 * Word. Đây là cách viết email HTML chạy được trên mọi trình mail, không phải thói quen cũ.
 *
 * <p>Màu chép từ {@code frontend/src/index.css} ({@code --color-brand}…) để thư cùng tông với web.
 * Email không đọc được biến CSS của frontend, nên đổi màu thương hiệu thì đổi ở cả hai chỗ.
 */
final class EmailTemplate {

    private static final String BRAND = "#4f46e5"; // --color-brand
    private static final String INK = "#111827"; // --color-ink
    private static final String INK_MUTED = "#5f6b7d"; // --color-ink-muted
    private static final String INK_SUBTLE = "#8a94a6"; // --color-ink-subtle
    private static final String BORDER = "#e4e7ee"; // --color-border
    private static final String PAGE_BG = "#f6f7fb"; // --color-surface-muted
    private static final String FONT = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private EmailTemplate() {}

    /** Bản văn bản thuần: mọi trình mail đọc được, và là bản dự phòng khi HTML bị chặn. */
    static String text(EmailContent content, String brand, String siteUrl) {
        StringBuilder out = new StringBuilder();
        out.append(greeting(content)).append("\n\n");
        paragraphs(out, content.intro());
        out.append(content.action().label()).append(":\n").append(content.action().url()).append("\n\n");
        paragraphs(out, content.outro());
        out.append("--\n")
                .append(brand)
                .append(" · ")
                .append(siteUrl)
                .append("\nThư gửi tự động, vui lòng không trả lời thư này.\n");
        return out.toString();
    }

    /** Bản HTML. Mọi chuỗi động đi qua {@link #esc} — xem Javadoc của lớp. */
    static String html(EmailContent content, String brand, String siteUrl) {
        String url = esc(content.action().url());
        StringBuilder body = new StringBuilder();
        body.append(p(esc(greeting(content)), INK));
        content.intro().forEach(line -> body.append(p(esc(line), INK)));
        body.append(button(esc(content.action().label()), url));
        body.append("<p style=\"margin:0 0 20px;font-size:13px;line-height:1.6;color:")
                .append(INK_MUTED)
                .append(";\">Nút không bấm được? Sao chép liên kết dưới đây vào trình duyệt:<br>")
                .append("<a href=\"")
                .append(url)
                .append("\" style=\"color:")
                .append(BRAND)
                .append(";word-break:break-all;\">")
                .append(url)
                .append("</a></p>");
        content.outro().forEach(line -> body.append(p(esc(line), INK_MUTED)));

        return """
                <!doctype html>
                <html lang="vi">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%1$s</title>
                </head>
                <body style="margin:0;padding:0;background:%5$s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%2$s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%5$s;">
                <tr><td align="center" style="padding:32px 12px;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid %6$s;border-radius:12px;">
                <tr><td style="padding:20px 32px;border-bottom:1px solid %6$s;font-family:%7$s;font-size:18px;font-weight:700;color:%8$s;">%3$s</td></tr>
                <tr><td style="padding:32px;font-family:%7$s;">
                <h1 style="margin:0 0 20px;font-size:20px;line-height:1.3;color:%9$s;">%2$s</h1>
                %4$s
                </td></tr>
                <tr><td style="padding:16px 32px;border-top:1px solid %6$s;font-family:%7$s;font-size:12px;line-height:1.6;color:%10$s;">
                Thư gửi tự động từ %3$s (<a href="%11$s" style="color:%10$s;">%12$s</a>). Vui lòng không trả lời thư này.
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """
                .formatted(
                        esc(content.subject()), // 1
                        esc(content.heading()), // 2
                        esc(brand), // 3
                        body, // 4 — đã escape từng mảnh ở trên
                        PAGE_BG, // 5
                        BORDER, // 6
                        FONT, // 7
                        BRAND, // 8
                        INK, // 9
                        INK_SUBTLE, // 10
                        esc(siteUrl), // 11
                        esc(displayHost(siteUrl))); // 12
    }

    private static String greeting(EmailContent content) {
        String name = content.recipientName();
        return name == null || name.isBlank() ? "Chào bạn," : "Chào " + name.trim() + ",";
    }

    private static void paragraphs(StringBuilder out, List<String> lines) {
        lines.forEach(line -> out.append(line).append("\n\n"));
    }

    /** {@code html} PHẢI là chuỗi đã escape. */
    private static String p(String html, String color) {
        return "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:" + color + ";\">" + html + "</p>";
    }

    /** {@code label} và {@code url} PHẢI là chuỗi đã escape. */
    private static String button(String label, String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 24px;\">"
                + "<tr><td style=\"border-radius:8px;background:" + BRAND + ";\">"
                + "<a href=\"" + url + "\" style=\"display:inline-block;padding:12px 28px;font-family:" + FONT
                + ";font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;\">"
                + label + "</a></td></tr></table>";
    }

    /** {@code https://vsleuth.id.vn/} → {@code vsleuth.id.vn}; URL lạ thì trả nguyên văn. */
    static String displayHost(String siteUrl) {
        try {
            String host = URI.create(siteUrl).getHost();
            return host == null ? siteUrl : host;
        } catch (IllegalArgumentException e) {
            return siteUrl;
        }
    }

    /** Escape cho cả nội dung thẻ lẫn giá trị thuộc tính (có nháy kép). Giữ nguyên chữ tiếng Việt. */
    static String esc(String raw) {
        return raw == null ? "" : HtmlUtils.htmlEscape(raw, StandardCharsets.UTF_8.name());
    }
}
