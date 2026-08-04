package vn.hust.datn.evidence.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mở rộng 1:1 của {@link EvidenceRecord} cho cảnh báo tĩnh (SpotBugs/Semgrep).
 *
 * <p>Không lặp file_path/start_line/symbol — chúng đã có trên EvidenceRecord. "Trích dẫn một
 * warning" và "trích dẫn bất kỳ evidence" nhờ vậy là CÙNG một cơ chế (join qua evidence_record_id
 * duy nhất) — xem V2__evidence_model.sql để biết đầy đủ lý do.
 */
@Entity
@Table(name = "warning")
@Getter
@Setter
@NoArgsConstructor
public class Warning {

    @Id
    @Column(name = "evidence_record_id")
    private UUID evidenceRecordId;

    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "evidence_record_id")
    private EvidenceRecord evidenceRecord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarningTool tool;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_id_normalized", nullable = false)
    private String ruleIdNormalized;

    /**
     * Priority/level nguyên bản của tool nếu có; NULL không có nghĩa là thiếu dữ liệu — SpotBugs
     * không có khái niệm "level" (chỉ có priority/rank riêng).
     */
    @Column(name = "severity_raw")
    private String severityRaw;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_normalized", nullable = false)
    private WarningSeverity severityNormalized;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cwe_ids")
    private Integer[] cweIds;

    /**
     * false cho 5/28 BugInstance thật không có SourceLine primary='true' (vẫn có đúng 1 vị trí,
     * nhưng parser phải tự chọn theo quy tắc fallback — xem docs/evidence-model.md).
     */
    @Column(name = "has_primary_location", nullable = false)
    private boolean hasPrimaryLocation = true;

    @Column(name = "source_index", nullable = false)
    private int sourceIndex;

    /**
     * Thứ tự sắp xếp theo mức nghiêm trọng (HIGH=0 … INFO=3), sinh từ {@link #severityNormalized}
     * ở tầng DB — chỉ đọc.
     *
     * <p>Tồn tại vì {@code ORDER BY severity_normalized} là thứ tự CHỮ CÁI: HIGH, INFO, LOW,
     * MEDIUM. Sai mà rất khó phát hiện qua mắt thường vì HIGH vẫn đứng đầu. Mọi truy vấn sắp xếp
     * theo mức nghiêm trọng phải dùng cột này.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "severity_rank", insertable = false, updatable = false)
    private Integer severityRank;
}
