package vn.hust.datn.evidence.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Luồng gửi email (xem {@code MailDispatcher}). Một luồng là đủ: lượng thư là vài cái mỗi giờ.
 * Hàng đợi có trần và tràn thì CHẠY TRÊN LUỒNG GỌI (CallerRuns) — thà một request chậm còn hơn
 * lặng lẽ đánh rơi email xác thực.
 */
@Configuration
public class MailExecutorConfig {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
