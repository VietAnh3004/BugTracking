package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Run kẹt {@code RUNNING} khi JVM chết giữa chừng — triệu chứng gặp thật: tắt Docker Desktop lúc
 * sandbox đang chạy, bật lại thì FE hiện "Đang phân tích…" suốt 4 ngày và khoá nút chạy lại.
 */
class OrphanedRunRecoveryTest extends AbstractIntegrationTest {

    @Autowired
    private OrphanedRunRecovery recovery;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runRunningBatDauTruocMocBiHaXuongFailedKemLyDo() {
        AnalysisRun orphan = runningStartedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(4));

        int failed = recovery.failRunsStartedBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        assertThat(failed).isGreaterThanOrEqualTo(1);
        AnalysisRun reloaded = fixtures.reloadRun(orphan.getId());
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(reloaded.getErrorMessage()).isEqualTo(OrphanedRunRecovery.REASON);
        assertThat(reloaded.getFinishedAt()).isNotNull();
    }

    /** Run tạo SAU mốc là việc của JVM hiện tại (đang chạy hoặc đang chờ hàng đợi) — không được đụng. */
    @Test
    void runRunningBatDauSauMocGiuNguyen() {
        AnalysisRun current = runningStartedAt(OffsetDateTime.now(ZoneOffset.UTC));

        recovery.failRunsStartedBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        assertThat(fixtures.reloadRun(current.getId()).getStatus()).isEqualTo(AnalysisRunStatus.RUNNING);
    }

    @Test
    void runDaKetThucKhongBiGhiDe() {
        AnalysisRun done = fixtures.runWithStatus(snapshot(), AnalysisRunStatus.SUCCEEDED);
        setStartedAt(done.getId(), OffsetDateTime.now(ZoneOffset.UTC).minusDays(4));

        recovery.failRunsStartedBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        AnalysisRun reloaded = fixtures.reloadRun(done.getId());
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(reloaded.getErrorMessage()).isNull();
    }

    private AnalysisRun runningStartedAt(OffsetDateTime startedAt) {
        AnalysisRun run = fixtures.runWithStatus(snapshot(), AnalysisRunStatus.RUNNING);
        setStartedAt(run.getId(), startedAt);
        return run;
    }

    private RepositorySnapshot snapshot() {
        return fixtures.snapshot(fixtures.project("orphan-%d".formatted(System.nanoTime())));
    }

    private void setStartedAt(UUID runId, OffsetDateTime startedAt) {
        jdbcTemplate.update("UPDATE analysis_run SET started_at = ? WHERE id = ?", startedAt, runId);
    }
}
