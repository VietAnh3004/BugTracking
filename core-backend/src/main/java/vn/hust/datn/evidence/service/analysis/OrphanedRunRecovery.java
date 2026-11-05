package vn.hust.datn.evidence.service.analysis;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;

/**
 * Hạ các run kẹt {@code RUNNING} của LẦN CHẠY TRƯỚC xuống {@code FAILED} khi khởi động.
 *
 * <h2>Vì sao run kẹt được</h2>
 *
 * Run sandbox chạy trên {@code sandboxExecutor} — hàng đợi TRONG BỘ NHỚ. JVM chết giữa chừng (tắt
 * Docker Desktop, deploy lại, OOM) là hàng đợi mất theo, và không còn ai gọi {@code finalizeRun}:
 * hàng {@code analysis_run} nằm ở {@code RUNNING} vĩnh viễn. Hậu quả thấy được: FE hiện "Đang phân
 * tích…" mãi và khoá nút chạy lại. {@code SandboxReaper} dọn được container mồ côi nhưng không đụng
 * tới DB — đây là nửa còn lại.
 *
 * <h2>Vì sao mốc là thời điểm JVM khởi động, không phải "RUNNING quá N phút"</h2>
 *
 * Một run bắt đầu trước khi JVM NÀY khởi động thì chắc chắn không ai đang xử lý nó. Ngược lại,
 * "quá N phút" sẽ giết nhầm run đang chờ trong hàng đợi một luồng phía sau một run khác. Mốc JVM
 * cũng không đụng tới run do request đầu tiên tạo ra trong khoảng Tomcat đã nhận request mà
 * {@link ApplicationReadyEvent} chưa bắn.
 *
 * <p>Giả định: core-backend chạy MỘT instance (hàng đợi sandbox vốn đã trong bộ nhớ). Chạy nhiều
 * instance dùng chung DB thì instance khởi động sau sẽ hạ nhầm run của instance kia.
 *
 * <p>Không gắn {@code @ConditionalOnProperty} theo sandbox: run kẹt từ lúc sandbox còn bật vẫn
 * phải được dọn khi sandbox đã tắt — và một lần nạp tay cũng để lại {@code RUNNING} nếu JVM chết
 * giữa request.
 */
@Service
public class OrphanedRunRecovery {

    private static final Logger log = LoggerFactory.getLogger(OrphanedRunRecovery.class);

    static final String REASON = "Phân tích bị gián đoạn: core-backend đã dừng (tắt Docker, khởi động lại hoặc hết bộ nhớ) "
            + "trong lúc lần phân tích này đang chạy. Chạy lại để có kết quả.";

    private final AnalysisRunRepository runRepository;

    public OrphanedRunRecovery(AnalysisRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    /** Không bao giờ ném: một ngoại lệ ở đây làm hỏng khởi động ứng dụng vì một việc dọn dẹp. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        Instant jvmStart = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        try {
            int failed = failRunsStartedBefore(OffsetDateTime.ofInstant(jvmStart, ZoneOffset.UTC));
            if (failed > 0) {
                log.warn("Da ha {} run ket RUNNING cua lan chay truoc xuong FAILED", failed);
            }
        } catch (RuntimeException e) {
            log.warn("Khong ha duoc run ket RUNNING lan nay: {}", e.getMessage());
        }
    }

    /**
     * KHÔNG mang {@code @Transactional}: {@link #recoverOnStartup} gọi nó từ chính lớp này
     * (self-invocation, không qua proxy), nên annotation ở đây sẽ im lặng không có tác dụng. Ranh
     * giới transaction nằm trên chính method repository.
     */
    public int failRunsStartedBefore(OffsetDateTime cutoff) {
        // MICROS: Postgres lưu tới micro giây — cùng lý do đã ghi ở CLAUDE.md cho các mốc thời gian khác.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return runRepository.failRunningStartedBefore(
                cutoff, REASON, now, AnalysisRunStatus.RUNNING, AnalysisRunStatus.FAILED);
    }
}
