package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chứng minh "0 cảnh báo phải bị coi là lỗi" (ADR 0003) là ràng buộc DB THẬT, không chỉ một câu
 * trong tài liệu — đã hỏng thật hai lần trong dự án (SpotBugs trên JDK 25; find-sec-bugs khai
 * báo sai chỗ trong pom.xml), cả hai lần pipeline vẫn báo "thành công".
 */
class AnalysisRunNotSilentlyEmptyTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID snapshotId;

    @BeforeEach
    void taoSnapshotGoc() {
        UUID ownerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)",
                ownerId,
                "owner-" + ownerId + "@test.local",
                "!not-a-real-bcrypt-hash!",
                "Test Owner");

        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO project (id, owner_id, name) VALUES (?, ?, ?)", projectId, ownerId, "test-project");

        snapshotId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO repository_snapshot (id, project_id, source_ref, storage_key) VALUES (?, ?, ?, ?)",
                snapshotId,
                projectId,
                "main",
                "storage/key");
    }

    @Test
    void runSucceededVoiZeroFindingBiTuChoi() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO analysis_run (id, snapshot_id, status, finding_count) VALUES (?, ?, 'SUCCEEDED', 0)",
                        UUID.randomUUID(),
                        snapshotId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_run_not_silently_empty");
    }

    @Test
    void runSucceededCleanKhongCoLyDoBiTuChoi() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO analysis_run (id, snapshot_id, status, finding_count) VALUES (?, ?, 'SUCCEEDED_CLEAN', 0)",
                        UUID.randomUUID(),
                        snapshotId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_run_clean_is_explicit");
    }

    @Test
    void runSucceededCleanCoLyDoThiDuocChapNhan() {
        jdbcTemplate.update(
                "INSERT INTO analysis_run (id, snapshot_id, status, finding_count, clean_ack_note) "
                        + "VALUES (?, ?, 'SUCCEEDED_CLEAN', 0, ?)",
                UUID.randomUUID(),
                snapshotId,
                "Da chay canary tren file mau co loi biet truoc, analyzer con song.");
    }

    /**
     * Canary đỏ nghĩa là bộ rule không nạp được, hoặc analyzer không thật sự chạy — dù nó vẫn có
     * thể ra hàng chục cảnh báo từ những rule còn sống. Không được để một run như vậy thành
     * SUCCEEDED, vì mọi thứ phía sau (agent, thực nghiệm) sẽ coi nó là dữ liệu đầy đủ.
     */
    @Test
    void runSandboxSucceededMaCanaryDoBiTuChoi() {
        assertThatThrownBy(() -> insertSandboxRun("SUCCEEDED", 33, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_run_sandbox_needs_canary");
    }

    /** Không kiểm canary cũng nguy hiểm y như canary đỏ — NULL không phải là "coi như đạt". */
    @Test
    void runSandboxSucceededMaKhongKiemCanaryBiTuChoi() {
        assertThatThrownBy(() -> insertSandboxRun("SUCCEEDED", 33, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_run_sandbox_needs_canary");
    }

    /** Run sandbox FAILED thì canary đỏ là chuyện bình thường — đó thường chính là lý do nó hỏng. */
    @Test
    void runSandboxFailedVoiCanaryDoDuocChapNhan() {
        insertSandboxRun("FAILED", 0, false);
    }

    @Test
    void runSandboxSucceededVoiCanaryXanhDuocChapNhan() {
        insertSandboxRun("SUCCEEDED", 33, true);
    }

    /**
     * Run nạp thủ công KHÔNG có canary để mà kiểm, nên khai {@code canary_ok = true} ở đó là một
     * lời khai không có gì chống lưng. NULL mới là giá trị trung thực.
     */
    @Test
    void runNapThuCongKhongDuocKhaiCanary() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO analysis_run (id, snapshot_id, status, finding_count, artifact_source, canary_ok) "
                                + "VALUES (?, ?, 'SUCCEEDED', 33, 'MANUAL_UPLOAD', true)",
                        UUID.randomUUID(),
                        snapshotId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_run_canary_only_from_sandbox");
    }

    /** Mặc định là MANUAL_UPLOAD, nên mọi hàng có sẵn từ trước V6 vẫn hợp lệ. */
    @Test
    void runKhongKhaiNguonArtifactThiMacDinhLaNapThuCong() {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO analysis_run (id, snapshot_id, status, finding_count) VALUES (?, ?, 'SUCCEEDED', 1)",
                runId,
                snapshotId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT artifact_source FROM analysis_run WHERE id = ?", String.class, runId))
                .isEqualTo("MANUAL_UPLOAD");
    }

    private void insertSandboxRun(String status, int findingCount, Boolean canaryOk) {
        jdbcTemplate.update(
                """
                INSERT INTO analysis_run
                    (id, snapshot_id, status, finding_count, artifact_source,
                     analyzer_image_digest, source_root, canary_ok, canary_finding_count)
                VALUES (?, ?, ?, ?, 'DOCKER_SANDBOX', 'sha256:abc', 'src', ?, 3)
                """,
                UUID.randomUUID(),
                snapshotId,
                status,
                findingCount,
                canaryOk);
    }
}
