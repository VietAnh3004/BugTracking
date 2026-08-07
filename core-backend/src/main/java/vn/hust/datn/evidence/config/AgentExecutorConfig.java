package vn.hust.datn.evidence.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Luồng nền phục vụ các lượt hỏi đáp với Agent.
 *
 * <p>Tách khỏi pool của servlet container vì một lượt suy luận đi qua nhiều vòng gọi model và có
 * thể kéo dài hàng chục giây: giữ luồng của Tomcat suốt thời gian đó sẽ làm cạn pool ấy trước khi
 * cạn bất cứ tài nguyên nào khác, và khi đó cả những endpoint không liên quan cũng ngừng phục vụ.
 *
 * <p>Nhiều luồng hơn sandbox (mỗi lượt chủ yếu là CHỜ mạng, không phải tính toán) nhưng vẫn có
 * trần: mỗi lượt tiêu tiền thật ở phía nhà cung cấp LLM, nên một hàng đợi vô hạn là một hoá đơn
 * vô hạn. {@code AbortPolicy} cho ra 503 đọc được thay vì để yêu cầu chờ vô thời hạn.
 */
@Configuration
public class AgentExecutorConfig {

    @Bean("agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Chờ lượt đang chạy hoàn tất khi tắt ứng dụng: khác sandbox, việc dở dang ở đây đã tiêu
        // token thật rồi, nên bỏ giữa chừng là vứt tiền và mất luôn dòng llm_trace của nó.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
