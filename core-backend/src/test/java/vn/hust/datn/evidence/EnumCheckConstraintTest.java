package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ClaimVerdict;
import vn.hust.datn.evidence.domain.CoverageStatus;
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceStatus;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.RefreshTokenRevokedReason;
import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotSourceType;
import vn.hust.datn.evidence.domain.SnapshotStatus;
import vn.hust.datn.evidence.domain.StackFrameKind;
import vn.hust.datn.evidence.domain.TestCaseStatus;
import vn.hust.datn.evidence.domain.TestFailureKind;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Đối chiếu TỪNG CHECK constraint kiểu enum trong V2 với tập giá trị của Java enum tương ứng.
 *
 * <p>Đây là phần bù cho quyết định dùng TEXT + CHECK thay vì enum native Postgres (xem ADR 0004):
 * enum native thì {@code ddl-auto: validate} tự bắt lệch, còn CHECK thì validate bỏ qua hoàn
 * toàn — test này thay thế đúng vai trò đó, và tệ hại đến mấy thì cũng chỉ đỏ ở CI chứ không lộ
 * ra khi đã chạy thực nghiệm.
 */
class EnumCheckConstraintTest extends AbstractIntegrationTest {

    private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']+)'::");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void analysisRunStatus() {
        assertCheckMatchesEnum("analysis_run", "status", AnalysisRunStatus.values());
    }

    @Test
    void evidenceRecordType() {
        assertCheckMatchesEnum("evidence_record", "type", EvidenceType.values());
    }

    @Test
    void evidenceRecordSource() {
        assertCheckMatchesEnum("evidence_record", "source", EvidenceSource.values());
    }

    @Test
    void evidenceRecordStatus() {
        assertCheckMatchesEnum("evidence_record", "status", EvidenceStatus.values());
    }

    @Test
    void warningTool() {
        assertCheckMatchesEnum("warning", "tool", WarningTool.values());
    }

    @Test
    void warningSeverityNormalized() {
        assertCheckMatchesEnum("warning", "severity_normalized", WarningSeverity.values());
    }

    @Test
    void evidenceLinkRelation() {
        assertCheckMatchesEnum("evidence_link", "relation", EvidenceLinkRelation.values());
    }

    @Test
    void evidenceLinkMethod() {
        assertCheckMatchesEnum("evidence_link", "method", EvidenceLinkMethod.values());
    }

    @Test
    void claimVerdict() {
        assertCheckMatchesEnum("claim", "verdict", ClaimVerdict.values());
    }

    @Test
    void claimDowngradedFromVerdict() {
        assertCheckMatchesEnum("claim", "downgraded_from_verdict", ClaimVerdict.values());
    }

    @Test
    void claimCitationResolution() {
        assertCheckMatchesEnum("claim_citation", "resolution", ClaimCitationResolution.values());
    }

    @Test
    void appUserRole() {
        assertCheckMatchesEnum("app_user", "role", UserRole.values());
    }

    @Test
    void refreshTokenRevokedReason() {
        assertCheckMatchesEnum("refresh_token", "revoked_reason", RefreshTokenRevokedReason.values());
    }

    @Test
    void repositorySnapshotStatus() {
        assertCheckMatchesEnum("repository_snapshot", "status", SnapshotStatus.values());
    }

    @Test
    void repositorySnapshotSourceType() {
        assertCheckMatchesEnum("repository_snapshot", "source_type", SnapshotSourceType.values());
    }

    @Test
    void snapshotFileEntryType() {
        assertCheckMatchesEnum("snapshot_file", "entry_type", SnapshotEntryType.values());
    }

    @Test
    void testCaseStatus() {
        assertCheckMatchesEnum("test_case", "status", TestCaseStatus.values());
    }

    @Test
    void testCaseFailureKind() {
        assertCheckMatchesEnum("test_case", "failure_kind", TestFailureKind.values());
    }

    @Test
    void stackFrameKind() {
        assertCheckMatchesEnum("stack_frame", "frame_kind", StackFrameKind.values());
    }

    /**
     * Cột này là GENERATED nên biểu thức sinh giá trị và CHECK là hai chỗ khác nhau — test này
     * canh CHECK. Nếu ai đó thêm giá trị vào biểu thức CASE mà quên CHECK, INSERT sẽ đỏ lúc chạy
     * chứ không phải lúc build; canh ở đây để lỗi lộ ra ngay trong CI.
     */
    @Test
    void coverageLineStatus() {
        assertCheckMatchesEnum("coverage_line", "status", CoverageStatus.values());
    }

    private void assertCheckMatchesEnum(String table, String column, Enum<?>[] enumValues) {
        String constraintName = table + "_" + column + "_check";
        String definition = jdbcTemplate.queryForObject(
                """
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = ?::regclass AND conname = ?
                """,
                String.class,
                table,
                constraintName);

        assertThat(definition)
                .as("Khong tim thay CHECK constraint '%s' tren bang '%s'", constraintName, table)
                .isNotNull();

        Matcher matcher = QUOTED_LITERAL.matcher(definition);
        List<String> dbValues = new java.util.ArrayList<>();
        while (matcher.find()) {
            dbValues.add(matcher.group(1));
        }

        List<String> javaValues =
                Arrays.stream(enumValues).map(Enum::name).collect(Collectors.toList());

        assertThat(dbValues)
                .as("CHECK constraint '%s' phai khop tuyet doi voi %s.values()", constraintName, enumValues[0]
                        .getDeclaringClass()
                        .getSimpleName())
                .containsExactlyInAnyOrderElementsOf(javaValues);
    }
}
