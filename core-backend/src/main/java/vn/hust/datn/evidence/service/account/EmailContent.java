package vn.hust.datn.evidence.service.account;

import java.util.List;
import java.util.Objects;

/**
 * Nội dung một email tài khoản, CHƯA định dạng. {@link EmailTemplate} biến nó thành cả bản văn bản
 * thuần lẫn bản HTML — một nguồn, hai bản, nên hai bản không thể nói hai điều khác nhau.
 *
 * <p>Mọi chuỗi ở đây là văn bản THÔ (chưa escape). Escape là việc của {@link EmailTemplate}, không
 * phải của người dựng nội dung — để không có chuyện chỗ này nhớ escape còn chỗ kia quên.
 *
 * @param subject tiêu đề thư
 * @param heading dòng tiêu đề lớn trong thân thư
 * @param recipientName tên hiển thị của người nhận — do NGƯỜI DÙNG đặt, tức dữ liệu không tin cậy
 * @param intro các đoạn trước nút hành động
 * @param action nút hành động (link xác thực, đặt lại mật khẩu…)
 * @param outro các đoạn sau nút — thường là "nếu không phải bạn thì…"
 */
public record EmailContent(
        String subject,
        String heading,
        String recipientName,
        List<String> intro,
        Action action,
        List<String> outro) {

    public EmailContent {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(action, "action");
        intro = List.copyOf(intro);
        outro = List.copyOf(outro);
    }

    /** Nút bấm trong bản HTML; trong bản văn bản thuần là nhãn kèm đường link đầy đủ. */
    public record Action(String label, String url) {

        public Action {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(url, "url");
        }
    }
}
