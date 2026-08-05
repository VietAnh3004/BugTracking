package vn.hust.datn.evidence.service.analysis.sandbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.model.Container;

/**
 * Dọn container và volume mồ côi của sandbox.
 *
 * <h2>Vì sao cần, khi {@code DockerSandboxRunner} đã dọn trong {@code finally}</h2>
 *
 * Khối {@code finally} chỉ chạy khi tiến trình còn sống. Nếu JVM bị giết giữa chừng — deploy lại,
 * OOM, mất điện — container vẫn đang chạy và volume vẫn còn, <b>vĩnh viễn</b>: không còn ai biết
 * chúng tồn tại. Mỗi lần rò để lại một container 2 GB RAM và một volume chứa cả cây mã nguồn của
 * người dùng. Đây là lưới an toàn, không phải cơ chế dọn chính.
 *
 * <h2>Hai thời điểm, vì chúng bắt hai tình huống khác nhau</h2>
 *
 * <ul>
 *   <li>{@link ApplicationReadyEvent} — dọn rác của <i>lần chạy trước</i> ngay khi khởi động lại.
 *       Đây chính là lúc rác chắc chắn tồn tại nếu tiến trình trước đã chết bất thường.
 *   <li>{@code @Scheduled} — dọn rác sinh ra trong lúc ứng dụng vẫn đang chạy (một luồng nền chết,
 *       daemon từ chối lệnh xoá tạm thời...).
 * </ul>
 *
 * <h2>Thời gian ân hạn là bắt buộc</h2>
 *
 * Không được xoá theo nhãn một cách mù quáng: một run đang chạy cũng mang đúng nhãn đó. Chỉ đụng
 * tới container KHÔNG còn chạy và đã tạo quá {@link #GRACE_PERIOD}. Volume thì để Docker tự từ
 * chối nếu còn ai dùng — rẻ hơn và đáng tin hơn là tự suy luận.
 */
@Service
@ConditionalOnProperty(name = "app.sandbox.runner", havingValue = "docker")
public class SandboxReaper {

    private static final Logger log = LoggerFactory.getLogger(SandboxReaper.class);

    /** Khớp nhãn mà {@link DockerSandboxRunner} gắn. */
    static final String LABEL_RUN_ID = "evidence.run-id";

    /**
     * Rộng hơn {@code app.sandbox.timeout} mặc định (15 phút) một khoảng an toàn: một container
     * vừa bị timeout giết vẫn đang được đọc log, xoá nó ngay là cướp mất chứng cứ chẩn đoán.
     */
    private static final Duration GRACE_PERIOD = Duration.ofHours(1);

    // Hang so bien dich (khong dung Duration.toMillis()): @Scheduled doi mot constant expression.
    private static final long INTERVAL_MS = 30L * 60 * 1000;

    private final DockerClient dockerClient;

    public SandboxReaper(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reapOnStartup() {
        log.info("Don rac sandbox cua lan chay truoc (neu co)");
        reap();
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void reapPeriodically() {
        reap();
    }

    /**
     * Không bao giờ ném: nó chạy trên luồng scheduler và trong {@code ApplicationReadyEvent} —
     * một ngoại lệ ở đây sẽ làm hỏng khởi động ứng dụng vì một việc dọn dẹp.
     */
    void reap() {
        try {
            int containers = reapContainers();
            int volumes = reapVolumes();
            if (containers > 0 || volumes > 0) {
                log.warn("Da don {} container va {} volume mo coi cua sandbox", containers, volumes);
            }
        } catch (RuntimeException e) {
            log.warn("Khong don duoc rac sandbox lan nay: {}", e.getMessage());
        }
    }

    private int reapContainers() {
        List<Container> candidates = dockerClient
                .listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(List.of(LABEL_RUN_ID))
                .exec();

        long cutoff = Instant.now().minus(GRACE_PERIOD).getEpochSecond();
        int removed = 0;
        for (Container container : candidates) {
            // "running" = một run đang thật sự làm việc, mang đúng nhãn này. Đụng vào là giết một
            // lần phân tích hợp lệ.
            if ("running".equalsIgnoreCase(container.getState())) {
                continue;
            }
            if (container.getCreated() != null && container.getCreated() > cutoff) {
                continue;
            }
            try {
                dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                removed++;
            } catch (RuntimeException e) {
                log.warn("Khong xoa duoc container mo coi {}: {}", container.getId(), e.getMessage());
            }
        }
        return removed;
    }

    private int reapVolumes() {
        List<InspectVolumeResponse> volumes = dockerClient
                .listVolumesCmd()
                .withFilter("label", List.of(LABEL_RUN_ID))
                .exec()
                .getVolumes();
        if (volumes == null) {
            return 0;
        }

        int removed = 0;
        for (InspectVolumeResponse volume : volumes) {
            try {
                dockerClient.removeVolumeCmd(volume.getName()).exec();
                removed++;
            } catch (RuntimeException e) {
                // Volume còn container dùng thì Docker từ chối — đúng hành vi mong muốn, và rẻ
                // hơn là tự suy luận xem ai đang giữ nó.
                log.debug("Bo qua volume {} (co the dang duoc dung): {}", volume.getName(), e.getMessage());
            }
        }
        return removed;
    }
}
