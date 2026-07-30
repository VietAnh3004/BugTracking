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
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceStatus;
import vn.hust.datn.evidence.domain.EvidenceType;
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
