package vn.hust.datn.evidence.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Độ phủ cấp method.
 *
 * <p>KHÔNG có quan hệ nào tới {@link CoverageLine}: phát hiện 3 cho thấy quan hệ đó không tồn tại
 * trong dữ liệu thật. JaCoCo chỉ cho {@code method/@line} = dòng ĐẦU, không có dòng cuối, và
 * method không theo thứ tự dòng — suy ra "dòng 42 thuộc method nào" là bịa dữ liệu.
 */
@Entity
@Table(name = "coverage_method")
@Getter
@Setter
@NoArgsConstructor
public class CoverageMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    /** Dạng JaCoCo, phân tách bằng {@code /}. */
    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "method_name", nullable = false)
    private String methodName;

    /** Descriptor JVM — phân biệt overload. */
    @Column(name = "method_desc", nullable = false)
    private String methodDesc;

    /** Dòng đầu của method; KHÔNG có dòng cuối vì JaCoCo không cung cấp. */
    @Column(name = "first_line")
    private Integer firstLine;

    @Column(name = "missed_instructions", nullable = false)
    private int missedInstructions;

    @Column(name = "covered_instructions", nullable = false)
    private int coveredInstructions;

    @Column(name = "missed_branches", nullable = false)
    private int missedBranches;

    @Column(name = "covered_branches", nullable = false)
    private int coveredBranches;

    @Column(name = "missed_lines", nullable = false)
    private int missedLines;

    @Column(name = "covered_lines", nullable = false)
    private int coveredLines;
}
