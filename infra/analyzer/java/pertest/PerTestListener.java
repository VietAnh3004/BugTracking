package evidence.pertest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Ghi dữ liệu JaCoCo RIÊNG cho từng test — để biết test nào chạy qua dòng nào, thay vì chỉ có
 * độ phủ gộp của cả bộ test (thứ buộc backend đoán liên kết test ↔ cảnh báo theo tên lớp).
 *
 * <p>Cơ chế: trước mỗi test, xin agent JaCoCo dữ liệu hiện có và RESET (bỏ đi); sau test, xin lại
 * và reset — phần nhận được chính là những gì RIÊNG test đó chạy qua. Ghi ra
 * {@code <thư mục>/<số thứ tự>.exec} kèm một dòng trong {@code manifest.tsv}.
 *
 * <p>Gắn vào lượt chạy test qua ServiceLoader (file META-INF/services trong jar) và
 * {@code -Dmaven.test.additionalClasspath}: KHÔNG sửa pom hay mã nguồn của người dùng.
 *
 * <p>Gọi agent bằng REFLECTION: jar này không phụ thuộc lúc biên dịch vào JaCoCo, và khi không có
 * agent (người dùng chạy test thường) hay không có biến môi trường thì nó im lặng không làm gì —
 * không bao giờ được làm hỏng lượt chạy test của người dùng.
 */
public final class PerTestListener implements TestExecutionListener {

    private final Path dir;
    private final Object agent;
    private final Method getExecutionData;
    private final AtomicInteger sequence = new AtomicInteger();

    public PerTestListener() {
        String dirEnv = System.getenv("EVIDENCE_PERTEST_DIR");
        Object foundAgent = null;
        Method foundMethod = null;
        if (dirEnv != null && !dirEnv.isBlank()) {
            try {
                Class<?> rt = Class.forName("org.jacoco.agent.rt.RT");
                foundAgent = rt.getMethod("getAgent").invoke(null);
                foundMethod = foundAgent.getClass().getMethod("getExecutionData", boolean.class);
                foundMethod.setAccessible(true);
                Files.createDirectories(Path.of(dirEnv));
            } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError e) {
                System.err.println("[evidence-pertest] tat: khong tim thay agent JaCoCo (" + e + ")");
                foundAgent = null;
                foundMethod = null;
            }
        }
        this.dir = dirEnv == null ? null : Path.of(dirEnv);
        this.agent = foundAgent;
        this.getExecutionData = foundMethod;
    }

    @Override
    public void executionStarted(TestIdentifier identifier) {
        if (enabled() && identifier.isTest()) {
            // Phần chạy GIỮA hai test (khởi tạo lớp, @BeforeAll…) không thuộc về test nào — nhưng
            // KHÔNG được vứt: reset của agent xoá luôn nó khỏi độ phủ GỘP. Ghi ra file riêng để
            // analyze.sh gộp lại trước khi sinh jacoco.xml (lỗi gặp thật: bản đầu vứt phần này
            // và jacoco.xml còn 0 dòng được phủ).
            writeBetween(dump());
        }
    }

    @Override
    public void testPlanExecutionFinished(org.junit.platform.launcher.TestPlan testPlan) {
        if (enabled()) {
            writeBetween(dump());
        }
    }

    private void writeBetween(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        try {
            Files.write(dir.resolve("between-" + sequence.get() + "-" + System.nanoTime() + ".exec"), data);
        } catch (IOException e) {
            System.err.println("[evidence-pertest] khong ghi duoc du lieu giua hai test: " + e);
        }
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        if (!enabled() || !identifier.isTest()) {
            return;
        }
        byte[] data = dump();
        if (data == null || !(identifier.getSource().orElse(null) instanceof MethodSource source)) {
            return;
        }
        int seq = sequence.incrementAndGet();
        String status = switch (result.getStatus()) {
            case SUCCESSFUL -> "PASSED";
            case FAILED -> "FAILED";
            case ABORTED -> "SKIPPED";
        };
        try {
            Files.write(dir.resolve(seq + ".exec"), data);
            String line = seq + "\t" + source.getClassName() + "\t" + source.getMethodName() + "\t" + status + "\n";
            Files.writeString(
                    dir.resolve("manifest.tsv"),
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[evidence-pertest] khong ghi duoc du lieu cua test " + seq + ": " + e);
        }
    }

    private boolean enabled() {
        return agent != null && dir != null;
    }

    private byte[] dump() {
        try {
            return (byte[]) getExecutionData.invoke(agent, true);
        } catch (ReflectiveOperationException e) {
            System.err.println("[evidence-pertest] khong lay duoc du lieu JaCoCo: " + e);
            return null;
        }
    }
}
