package vn.hust.datn.evidence.service.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.hust.datn.evidence.domain.StackFrameKind;

/**
 * Tách text thô của một stack trace thành từng frame đã phân loại.
 *
 * <h2>Đừng chép lại regex trong {@code case-study/summarize.py}</h2>
 *
 * Regex tham chiếu ở đó là {@code at ([\w.$]+)\.(\w+)\(([\w.]+):(\d+)\)} và nó SAI ở hai chỗ, cả
 * hai đều làm frame biến mất trong im lặng:
 *
 * <ul>
 *   <li>{@code [\w.$]+} không có {@code /} nên KHÔNG khớp được frame mang tiền tố module JPMS
 *       ({@code at java.base/java.lang.reflect.Method.invoke(Method.java:580)}) — mà mọi trace
 *       của JUnit đều có frame kiểu đó. Tương tự với tiền tố classloader {@code app//}.
 *   <li>{@code :(\d+)} bắt buộc có số dòng nên bỏ luôn {@code (Native Method)} và
 *       {@code (Unknown Source)}.
 * </ul>
 *
 * <p>Ở đây frame không parse được vẫn sinh ra hàng với {@link StackFrameKind#UNKNOWN} và giữ
 * {@code rawLine}. Nếu heuristic sai, ta muốn thấy một đống UNKNOWN chứ không phải một stack trace
 * ngắn đi một cách vô hại.
 */
public final class StackTraceParser {

    /**
     * {@code at [<module>/]<fqcn>.<method>(<phần trong ngoặc>)}.
     *
     * <p>Phần trong ngoặc để lỏng ({@code [^)]*}) rồi phân tích riêng, thay vì ép {@code File:123}
     * ngay trong regex — nhờ vậy {@code (Native Method)} vẫn thành frame.
     */
    // '//?' chu khong phai '/': tien to MODULE JPMS dung MOT gach ('java.base/...') con tien to
    // CLASSLOADER dung HAI ('app//...'). Chi nhan mot gach thi moi frame 'app//' roi xuong UNKNOWN.
    private static final Pattern FRAME = Pattern.compile("^at\\s+(?:([\\w.]+)//?)?([\\w.$]+)\\.([\\w$<>]+)\\(([^)]*)\\)$");

    /** {@code Foo.java:42} — tên file có thể chứa dấu cách nên không dùng {@code \w}. */
    private static final Pattern SOURCE = Pattern.compile("^([^:]+):(\\d+)$");

    private static final Set<String> JDK_PREFIXES = Set.of("java.", "javax.", "jdk.", "sun.", "com.sun.");

    private static final Set<String> TEST_FRAMEWORK_PREFIXES = Set.of(
            "org.junit.",
            "junit.",
            "org.opentest4j.",
            "org.assertj.",
            "org.hamcrest.",
            "org.mockito.",
            "org.testng.",
            "org.testcontainers.",
            "org.springframework.test.",
            "org.springframework.boot.test.");

    private StackTraceParser() {}

    /**
     * @param testClassName FQCN của lớp test — dùng để suy ra gốc package của dự án, tức là ranh
     *     giới giữa {@link StackFrameKind#PRODUCTION} và {@link StackFrameKind#LIBRARY}. Không có
     *     cách nào biết "mã của dự án" mà không có mốc này.
     */
    public static List<StackFrameDraft> parse(String rawTrace, String testClassName) {
        if (rawTrace == null || rawTrace.isBlank()) {
            return List.of();
        }
        String projectRoot = packageRootOf(testClassName);

        List<StackFrameDraft> frames = new ArrayList<>();
        int ordinal = 0;
        for (String line : rawTrace.split("\\R")) {
            String trimmed = line.trim();
            // Chỉ dòng 'at ' thành frame. 'Caused by:' và '... 27 more' KHÔNG phải frame — chúng
            // ở lại nguyên vẹn trong test_case.stack_trace_raw.
            if (!trimmed.startsWith("at ")) {
                continue;
            }
            frames.add(toFrame(trimmed, ordinal++, testClassName, projectRoot));
        }
        return frames;
    }

    private static StackFrameDraft toFrame(String rawLine, int ordinal, String testClassName, String projectRoot) {
        Matcher matcher = FRAME.matcher(rawLine);
        if (!matcher.matches()) {
            // Không bỏ qua: giữ hàng với UNKNOWN để lỗi heuristic hiện ra thành số.
            return new StackFrameDraft(ordinal, rawLine, null, null, null, null, null, StackFrameKind.UNKNOWN);
        }

        String modulePrefix = matcher.group(1);
        String declaringClass = matcher.group(2);
        String methodName = matcher.group(3);
        String location = matcher.group(4);

        String sourceFileName = null;
        Integer lineNumber = null;
        Matcher source = SOURCE.matcher(location);
        if (source.matches()) {
            sourceFileName = source.group(1);
            lineNumber = Integer.valueOf(source.group(2));
        }
        // location là "Native Method" / "Unknown Source" -> cả hai để null, đúng nghĩa "KHÔNG CÓ
        // tên file", khác với "có tên file nhưng không đối chiếu được".

        return new StackFrameDraft(
                ordinal,
                rawLine,
                modulePrefix,
                declaringClass,
                methodName,
                sourceFileName,
                lineNumber,
                classify(modulePrefix, declaringClass, testClassName, projectRoot));
    }

    private static StackFrameKind classify(
            String modulePrefix, String declaringClass, String testClassName, String projectRoot) {
        if (declaringClass == null) {
            return StackFrameKind.UNKNOWN;
        }
        if (modulePrefix != null && modulePrefix.startsWith("java.")) {
            return StackFrameKind.JDK;
        }
        if (startsWithAny(declaringClass, JDK_PREFIXES)) {
            return StackFrameKind.JDK;
        }
        if (startsWithAny(declaringClass, TEST_FRAMEWORK_PREFIXES)) {
            return StackFrameKind.TEST_FRAMEWORK;
        }
        if (projectRoot != null && declaringClass.startsWith(projectRoot)) {
            // Lớp test của chính dự án: trùng tên lớp test, là inner class của nó, hoặc theo quy
            // ước tên. Phần còn lại trong gốc package của dự án là mã production.
            String outer = declaringClass.split("\\$", 2)[0];
            if (outer.equals(testClassName) || isTestClassName(outer)) {
                return StackFrameKind.TEST;
            }
            return StackFrameKind.PRODUCTION;
        }
        return StackFrameKind.LIBRARY;
    }

    private static boolean isTestClassName(String fqcn) {
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return simpleName.endsWith("Test") || simpleName.endsWith("Tests") || simpleName.endsWith("IT");
    }

    /**
     * Ba segment đầu của package lớp test, vd {@code vn.hust.datn}. Đủ hẹp để không nuốt cả
     * {@code org.*} của thư viện, đủ rộng để mã production nằm ở package con bất kỳ vẫn được nhận.
     */
    private static String packageRootOf(String testClassName) {
        if (testClassName == null || testClassName.isBlank()) {
            return null;
        }
        String[] segments = testClassName.split("\\.");
        int take = Math.min(3, Math.max(1, segments.length - 1));
        return String.join(".", java.util.Arrays.copyOfRange(segments, 0, take));
    }

    private static boolean startsWithAny(String value, Set<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }
}
