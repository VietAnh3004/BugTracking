package vn.hust.datn.casestudy.vulnshop.util;

import java.util.Random;

/**
 * Sinh màu đại diện cho avatar người dùng chưa tải ảnh.
 *
 * <p>Lớp này KHÔNG có test — có chủ đích, để cảnh báo sinh ra ở đây không có bằng chứng kiểm thử.
 */
public final class ColorPicker {

    /**
     * SEEDED — false positive: {@link Random} không an toàn về mật mã.
     *
     * <p>Công cụ sẽ báo PREDICTABLE_RANDOM vì {@code java.util.Random} đoán trước được. Cảnh báo
     * đúng về mặt kỹ thuật nhưng không có ý nghĩa ở đây: giá trị sinh ra chỉ dùng để chọn màu nền
     * avatar. Đoán trước được màu avatar không mang lại lợi thế nào cho kẻ tấn công.
     *
     * <p>Dùng {@code SecureRandom} ở đây chỉ làm chậm chương trình mà không tăng an toàn.
     */
    private static final Random RANDOM = new Random();

    private static final String[] PALETTE = {"#ef4444", "#f97316", "#22c55e", "#3b82f6", "#a855f7"};

    private ColorPicker() {}

    /** Chọn ngẫu nhiên một màu trong bảng màu. */
    public static String randomColor() {
        return PALETTE[RANDOM.nextInt(PALETTE.length)];
    }

    /** Chọn màu ổn định theo tên người dùng — cùng tên luôn ra cùng màu. */
    public static String colorFor(String username) {
        int index = Math.abs(username.hashCode()) % PALETTE.length;
        return PALETTE[index];
    }
}
