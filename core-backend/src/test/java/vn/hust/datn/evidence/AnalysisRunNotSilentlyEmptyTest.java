package vn.hust.datn.evidence;

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
}
