package vn.hust.datn.evidence.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

/**
 * Client Docker cho sandbox — CHỈ được nạp khi {@code app.sandbox.runner=docker}.
 *
 * <p>Nếu tạo bean vô điều kiện, mọi môi trường không có Docker daemon (bao gồm phần lớn test) sẽ
 * kéo theo một bean chẳng ai dùng, và một tính năng phụ lại thành điều kiện để ứng dụng khởi động.
 *
 * <p>Cấu hình lấy từ môi trường ({@code DOCKER_HOST}, named pipe trên Windows, socket trên Linux)
 * qua {@code DefaultDockerClientConfig} — cùng cơ chế Testcontainers dùng, nên cách cấu hình đã
 * ghi trong CLAUDE.md cho test cũng áp dụng nguyên vẹn cho mã production.
 *
 * <p>Transport {@code zerodep} chứ không phải Netty/Apache: nó không kéo thêm HTTP stack nào, và
 * quan trọng hơn — đó là transport mà Testcontainers dùng, tức là transport đã được kiểm chứng
 * trên máy phát triển này (xem vết sẹo tương thích ghi ở {@code pom.xml}).
 */
@Configuration
@ConditionalOnProperty(name = "app.sandbox.runner", havingValue = "docker")
public class DockerClientConfig {

    /**
     * {@code destroyMethod = "close"}: không đóng thì tiến trình giữ kết nối tới daemon sau khi
     * context tắt, và test tích hợp rò connection qua từng context.
     */
    @Bean(destroyMethod = "close")
    public DockerClient dockerClient() {
        DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(20)
                // Timeout tầng HTTP — KHÁC hàng rào thời gian của container (app.sandbox.timeout).
                // Cái này chặn "daemon không trả lời"; cái kia chặn "analyzer chạy mãi".
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
