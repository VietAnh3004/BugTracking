package vn.hust.datn.evidence.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Độ phủ của một dòng. Cha là evidence_record cấp SOURCEFILE (một record cho cả file), không
 * phải một record cho mỗi dòng — xem V5__evidence_raw_tables.sql.
 *
 * <p>Giữ nguyên bốn bộ đếm gốc thay vì thu về boolean vì {@code PARTIAL} là bằng chứng khác hẳn
 * {@code COVERED} cho câu hỏi "cảnh báo này có được test phủ không".
 */
@Entity
@Table(name = "coverage_line")
@IdClass(CoverageLineId.class)
@Getter
@Setter
@NoArgsConstructor
public class CoverageLine {

    @Id
    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Id
    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    /** Missed instructions. */
    @Column(name = "mi", nullable = false)
    private int mi;

    /** Covered instructions — {@code ci > 0} chính là định nghĩa "dòng được phủ" của JaCoCo. */
    @Column(name = "ci", nullable = false)
    private int ci;

    /** Missed branches. */
    @Column(name = "mb", nullable = false)
    private int mb;

    /** Covered branches. */
    @Column(name = "cb", nullable = false)
    private int cb;

    // Postgres GENERATED ALWAYS AS (...) STORED — chỉ đọc, không bao giờ ghi từ tầng ứng dụng.
    @Generated(event = EventType.INSERT)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", insertable = false, updatable = false)
    private CoverageStatus status;
}
