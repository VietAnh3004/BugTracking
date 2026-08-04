package vn.hust.datn.evidence.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mở rộng 1:1 của {@link EvidenceRecord} cho {@code TEST_RESULT}/{@code FAILED_TEST}, theo đúng
 * khuôn của {@link Warning} (ADR 0004 quyết định 2): đúng một test case = đúng một đơn vị trích
 * dẫn, nên "trích dẫn một test" và "trích dẫn bất kỳ evidence" là cùng một cơ chế join.
 */
@Entity
@Table(name = "test_case")
@Getter
@Setter
@NoArgsConstructor
public class TestCase {

    @Id
    @Column(name = "evidence_record_id")
    private UUID evidenceRecordId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "evidence_record_id")
    private EvidenceRecord evidenceRecord;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "method_name", nullable = false)
    private String methodName;

    /** {@code <testcase @name>} nguyên văn — test tham số hoá có dạng {@code ten[1]}. */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestCaseStatus status;

    /** NULL khi PASSED/SKIPPED — ràng buộc {@code ck_test_case_failure_kind} ép điều đó. */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_kind")
    private TestFailureKind failureKind;

    @Column(name = "failure_type")
    private String failureType;

    @Column(name = "failure_message")
    private String failureMessage;

    /** Text thô nguyên vẹn — nguồn duy nhất để reparse khi heuristic tách frame sai. */
    @Column(name = "stack_trace_raw")
    private String stackTraceRaw;

    @Column(name = "duration_ms")
    private Integer durationMs;

    /**
     * Provenance CẤU TRÚC (trace này thuộc test nào), KHÔNG phải {@link EvidenceLink} ngữ nghĩa.
     * Nhờ cột này tuần 8-9 tra được trace của một test hỏng mà chưa cần EvidenceLinker.
     */
    @Column(name = "stack_trace_evidence_id")
    private UUID stackTraceEvidenceId;

    @Column(name = "report_file", nullable = false)
    private String reportFile;
}
