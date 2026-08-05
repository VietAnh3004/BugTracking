package vn.hust.datn.evidence.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Luồng nền chạy sandbox.
 *
 * <p><b>ĐÚNG MỘT luồng</b>, không phải để tiết kiệm: mỗi lần chạy chiếm 2 GB RAM và 2 CPU của
 * máy chủ (xem {@code app.sandbox.*}), nên hai lần chạy song song trên một máy phát triển là cách
 * chắc chắn để cả hai cùng chậm rồi cùng chạm timeout. Cần thông lượng cao hơn thì tách service
 * {@code analyzer-runner} riêng — {@code SandboxRunner} đã giữ chỗ cho việc đó.
 *
 * <p>Hàng đợi CÓ TRẦN và chính sách từ chối là {@code AbortPolicy}: hàng đợi vô hạn sẽ nhận mọi
 * yêu cầu rồi để chúng chờ hàng giờ, và người dùng thấy một run {@code RUNNING} bất động mà không
 * biết nó còn chưa được bắt đầu. Từ chối thẳng cho ra 503 đọc được.
 */
@Configuration
public class SandboxExecutorConfig {

    @Bean("sandboxExecutor")
    public Executor sandboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("sandbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Không chờ task đang chạy khi tắt ứng dụng: một lần phân tích có thể còn 10 phút nữa, và
        // container mồ côi đã có SandboxReaper + label evidence.run-id lo.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
