package vn.hust.datn.evidence.service.linker;

import java.util.List;
import java.util.Optional;

/**
 * Suy tên lớp production từ tên lớp test theo quy ước đặt tên: {@code FooTest} → {@code Foo}.
 *
 * <h2>Đây là suy luận YẾU NHẤT trong ba tầng, và nó phải tự khai điều đó</h2>
 *
 * Không có gì trong artifact chống lưng cho phép suy này — nó chỉ dựa vào việc lập trình viên đặt
 * tên theo thói quen. Liên kết sinh từ đây mang {@code method = NAMING_CONVENTION}, mức tin cậy
 * thấp nhất, để tuần 15 đo được đúng tỉ lệ bằng chứng chỉ dựa vào quy ước.
 *
 * <p>Vẫn cần vì phương án thay thế đắt hơn nhiều: JaCoCo chuẩn chỉ cho **một** {@code <sessioninfo>}
 * cho cả lần chạy, không cho biết test nào phủ dòng nào. Muốn biết chính xác phải chạy JaCoCo
 * per-test (tách execution theo từng test), thứ đã cân nhắc và hoãn.
 *
 * <h2>Trả {@code Optional}, và người gọi phải ĐẾM khi rỗng</h2>
 *
 * Một lớp test không suy được ra lớp production nào là chuyện bình thường (lớp base trừu tượng,
 * test tích hợp đặt tên theo kịch bản). Nhưng nếu <i>phần lớn</i> lớp test rơi vào đó thì quy ước
 * của repo khác với giả định ở đây, và hậu quả là mất bằng chứng trong im lặng.
 */
public final class TestClassNameConvention {

    /**
     * Hậu tố xét theo thứ tự DÀI TRƯỚC: {@code FooTests} phải khớp {@code Tests} rồi mới tới
     * {@code Test}, nếu không {@code FooTests} sẽ ra {@code Foos}.
     */
    private static final List<String> SUFFIXES = List.of("Tests", "Test", "IT", "ITCase", "TestCase");

    private static final String PREFIX = "Test";

    private TestClassNameConvention() {}

    /**
     * @param testClassName FQN của lớp test, vd {@code vn.hust.app.FooTest}
     * @return FQN lớp production tương ứng, hoặc rỗng khi không suy được
     */
    public static Optional<String> productionClassOf(String testClassName) {
        if (testClassName == null || testClassName.isBlank()) {
            return Optional.empty();
        }
        // Lớp lồng nhau: FooTest$WhenEmpty là một nhóm test CỦA FooTest, không phải một lớp test
        // riêng. Cắt phần sau '$' trước khi bỏ hậu tố, nếu không thì không hậu tố nào khớp.
        String outer = testClassName;
        int dollar = outer.indexOf('$');
        if (dollar >= 0) {
            outer = outer.substring(0, dollar);
        }

        int lastDot = outer.lastIndexOf('.');
        String packageName = lastDot < 0 ? "" : outer.substring(0, lastDot + 1);
        String simpleName = outer.substring(lastDot + 1);

        for (String suffix : SUFFIXES) {
            if (simpleName.length() > suffix.length() && simpleName.endsWith(suffix)) {
                return Optional.of(packageName + simpleName.substring(0, simpleName.length() - suffix.length()));
            }
        }
        if (simpleName.length() > PREFIX.length() && simpleName.startsWith(PREFIX)) {
            return Optional.of(packageName + simpleName.substring(PREFIX.length()));
        }
        return Optional.empty();
    }

    /**
     * Quy một tên lớp về dạng so sánh được, bỏ qua khác biệt {@code /} và {@code .}.
     *
     * <p>Cần vì bốn nguồn khai tên lớp theo ba kiểu: JaCoCo dùng {@code vn/hust/app/Foo} (giữ
     * nguyên văn để đối chiếu ngược lại artifact), SpotBugs dùng {@code vn.hust.app.Foo}, Surefire
     * cũng dùng dấu chấm. So thẳng chuỗi là không bao giờ khớp — và không khớp thì im lặng, chỉ
     * biểu hiện thành "không có bằng chứng liên quan".
     *
     * <p>Cũng bỏ luôn phần sau {@code $}: {@code Foo$Inner} và {@code Foo} thuộc cùng một file
     * nguồn, và độ phủ mức method của inner class vẫn nói lên điều gì đó về Foo.
     */
    public static String canonical(String className) {
        if (className == null) {
            return "";
        }
        String normalized = className.replace('/', '.');
        int dollar = normalized.indexOf('$');
        return dollar >= 0 ? normalized.substring(0, dollar) : normalized;
    }
}
