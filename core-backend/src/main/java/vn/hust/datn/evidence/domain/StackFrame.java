package vn.hust.datn.evidence.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng {@code at ...} đã diễn giải của evidence loại {@code STACK_TRACE}.
 *
 * <p>Tồn tại thành bảng riêng vì phát hiện 2: chỉ 2/5 test hỏng thật có frame trỏ về mã
 * production, nên heuristic phân loại frame là thứ sẽ phải sửa lại — và khi sửa phải reparse
 * được từ {@link #rawLine} mà không cần quay lại artifact gốc.
 */
@Entity
@Table(name = "stack_frame")
@IdClass(StackFrameId.class)
@Getter
@Setter
@NoArgsConstructor
public class StackFrame {

    @Id
    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Id
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "raw_line", nullable = false)
    private String rawLine;

    /** {@code java.base} (module JPMS) hoặc {@code app} (classloader); NULL nếu không có. */
    @Column(name = "module_prefix")
    private String modulePrefix;

    @Column(name = "declaring_class")
    private String declaringClass;

    @Column(name = "method_name")
    private String methodName;

    /** NULL cho {@code (Native Method)}/{@code (Unknown Source)} — khác với "có tên nhưng không đối chiếu được". */
    @Column(name = "source_file_name")
    private String sourceFileName;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "file_path")
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "frame_kind", nullable = false)
    private StackFrameKind frameKind;
}
