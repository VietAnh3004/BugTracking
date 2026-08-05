package vn.hust.datn.evidence.service.analysis.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import vn.hust.datn.evidence.AbstractIntegrationTest;

/**
 * Lưới an toàn cho rác của sandbox. Chạy thật trên Docker vì thứ cần kiểm là hành vi của daemon,
 * không phải logic Java.
 *
 * <p>Hai khẳng định đối nghịch nhau, và cái thứ hai mới là cái khó: reaper phải dọn được rác cũ
 * <b>mà không đụng vào một run đang chạy</b> — vốn mang đúng cùng một nhãn.
 */
@TestPropertySource(properties = "app.sandbox.runner=docker")
class SandboxReaperTest extends AbstractIntegrationTest {

    @Autowired
    private SandboxReaper reaper;

    @Autowired
    private DockerClient dockerClient;

    /** Image mồi cố ý là {@code alpine} chứ không phải image analyzer: ở đây kiểm CƠ CHẾ dọn rác. */
    static final String IMAGE = "alpine:3.20";

    /**
     * Kiểm cả daemon LẪN image mồi. Thiếu image mà để test đỏ thì nó sẽ nhanh chóng bị bỏ qua theo
     * thói quen — rồi bỏ qua luôn cả khi nó đỏ thật.
     */
    static boolean dockerSanSang() {
        try (var httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .build()
                        .getDockerHost())
                .build()) {
            var client = DockerClientImpl.getInstance(
                    DefaultDockerClientConfig.createDefaultConfigBuilder().build(), httpClient);
            client.pingCmd().exec();
            client.inspectImageCmd(IMAGE).exec();
            return true;
        } catch (Exception e) {
            System.out.println("Bo qua SandboxReaperTest: " + e.getMessage() + ". Chay 'docker pull " + IMAGE + "'.");
            return false;
        }
    }

    /**
     * Container VỪA tạo mang nhãn sandbox KHÔNG được đụng tới: nó có thể là một run đang chạy.
     * Thời gian ân hạn là thứ duy nhất phân biệt "rác" với "đang làm việc".
     */
    @Test
    @EnabledIf("dockerSanSang")
    void reap_khongDungVaoContainerVuaTao() {
        String runId = UUID.randomUUID().toString();
        String containerId = dockerClient
                .createContainerCmd(IMAGE)
                .withLabels(java.util.Map.of(SandboxReaper.LABEL_RUN_ID, runId))
                .withCmd("true")
                .exec()
                .getId();
        try {
            reaper.reap();

            assertThat(dockerClient
                            .listContainersCmd()
                            .withShowAll(true)
                            .withIdFilter(List.of(containerId))
                            .exec())
                    .as("container vua tao phai con nguyen — no co the la mot run dang chay")
                    .isNotEmpty();
        } finally {
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            } catch (RuntimeException ignored) {
                // Đã bị dọn thì thôi — mục đích của finally chỉ là không để rác lại sau test.
            }
        }
    }

    /** Chạy khi không có gì để dọn cũng phải im lặng đi qua, không ném. */
    @Test
    @EnabledIf("dockerSanSang")
    void reap_khongCoRac_khongNem() {
        reaper.reap();
        reaper.reap();
    }
}
