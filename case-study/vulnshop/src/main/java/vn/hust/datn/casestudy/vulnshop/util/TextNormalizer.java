package vn.hust.datn.casestudy.vulnshop.util;

import java.util.Locale;

/** Chuẩn hoá chuỗi trước khi so khớp. */
public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * SEEDED — false positive: {@code toLowerCase()} không truyền {@link Locale}.
     *
     * <p>SpotBugs báo DM_CONVERT_CASE — chính là cảnh báo đã gặp trong spike tuần 1. Cảnh báo này
     * có lý do: với locale Thổ Nhĩ Kỳ, chữ 'I' viết thường thành 'ı' chứ không phải 'i', nên mã
     * dùng locale mặc định có thể chạy khác nhau trên các máy khác nhau.
     *
     * <p>Nhưng ở đây đầu vào là mã trạng thái nội bộ, chỉ gồm chữ ASCII hoa (SHIPPED, CANCELLED),
     * không đến từ người dùng. Kết quả không đổi dù locale nào — đây là dương tính giả.
     *
     * <p>Giữ lại có chủ đích để đối chiếu với {@link #normalizeUserInput(String)} bên dưới, nơi
     * cùng một cảnh báo lại là lỗi thật.
     */
    public static String normalizeStatusCode(String statusCode) {
        return statusCode.toLowerCase();
    }

    /** Chuẩn hoá đúng cách: nêu rõ locale, dùng cho dữ liệu người dùng nhập. */
    public static String normalizeUserInput(String input) {
        return input.toLowerCase(Locale.ROOT).trim();
    }
}
