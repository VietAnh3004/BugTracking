package vn.hust.datn.evidence.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Chặn ứng dụng khởi động khi một biến môi trường bắt buộc bị thiếu.
 *
 * <p><b>Vì sao cần lớp này — Spring KHÔNG tự làm việc đó cho {@code @ConfigurationProperties}:</b>
 * {@code @Value} dùng placeholder resolver ở chế độ nghiêm ngặt và ném lỗi khi không giải được
 * {@code ${VAR}}, nhưng {@code @ConfigurationProperties} dùng
 * {@code PropertySourcesPlaceholdersResolver} với {@code ignoreUnresolvablePlaceholders = true} —
 * biến không giải được sẽ được bind NGUYÊN VĂN thành chuỗi {@code "${VAR}"}.
 *
 * <p>Hậu quả đã gặp thật khi kiểm chứng end-to-end tuần 5-7: bỏ {@code CORS_ORIGINS} thì ứng dụng
 * KHỞI ĐỘNG BÌNH THƯỜNG, {@code /actuator/health} trả {@code UP}, nhưng origin được phép là chuỗi
 * {@code "${CORS_ORIGINS}"} — mọi request từ trình duyệt bị chặn CORS. Người vận hành thấy một
 * ứng dụng "khoẻ mạnh" và đi tìm lỗi ở phía frontend. {@code @NotBlank} không cứu được vì
 * {@code "${CORS_ORIGINS}"} là chuỗi KHÔNG rỗng.
 *
 * <p>Đúng nguyên tắc §1bis.1 của kế hoạch: "biến không có giá trị mặc định là biến bắt buộc —
 * thiếu thì ứng dụng không khởi động được, thay vì chạy rồi lỗi mơ hồ lúc đang phục vụ request".
 *
 * <p>Chạy ở {@code ApplicationEnvironmentPreparedEvent} — sớm nhất có thể, trước khi bất kỳ bean
 * nào được tạo, nên thông điệp lỗi là thứ đầu tiên người vận hành nhìn thấy chứ không lẫn giữa
 * stack trace của một bean nào đó chết theo.
 */
public class UnresolvedPlaceholderValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Chỉ soi cấu hình của chính dự án; `spring.*` và thư viện bên thứ ba có quy ước riêng. */
    private static final String WATCHED_PREFIX = "app.";

    private static final Pattern UNRESOLVED = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        List<String> problems = new ArrayList<>();

        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                if (!key.startsWith(WATCHED_PREFIX)) {
                    continue;
                }
                collectProblem(environment, key, problems);
            }
        }

        if (!problems.isEmpty()) {
            String details = String.join(System.lineSeparator() + "  - ", problems);
            throw new IllegalStateException("Thiếu biến môi trường bắt buộc:" + System.lineSeparator()
                    + "  - " + details + System.lineSeparator()
                    + "Kiểm tra file .env (xem .env.example để biết danh sách đầy đủ).");
        }
    }

    private void collectProblem(ConfigurableEnvironment environment, String key, List<String> problems) {
        String rawValue;
        try {
            rawValue = environment.getProperty(key);
        } catch (RuntimeException e) {
            // Một số property source ném lỗi khi giải placeholder — đó cũng là biến thiếu.
            problems.add("%s (%s)".formatted(key, e.getMessage()));
            return;
        }
        if (rawValue == null) {
            return;
        }

        Matcher matcher = UNRESOLVED.matcher(rawValue);
        while (matcher.find()) {
            problems.add("%s cần biến môi trường %s".formatted(key, matcher.group(1)));
        }
    }
}
