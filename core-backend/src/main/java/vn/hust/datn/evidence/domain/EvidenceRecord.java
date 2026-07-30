package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đơn vị bằng chứng có thể trích dẫn (Evidence ID). Xem V2__evidence_model.sql cho lý do thiết
 * kế đầy đủ — đặc biệt: symbol/file_path/start_line/end_line đều nullable vì Semgrep (SARIF)
 * không có logicalLocations nên không bao giờ có symbol, và không phải evidence nào cũng có toạ
 * độ file:line.
 */
@Entity
@Table(name = "evidence_record")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seq_in_run", nullable = false)
    private int seqInRun;

    // Postgres GENERATED ALWAYS AS (...) STORED — chỉ đọc, không bao giờ ghi từ tầng ứng dụng.
    @Generated(event = EventType.INSERT)
    @Column(name = "short_id", insertable = false, updatable = false)
    private String shortId;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceSource source;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    /** FQN class#method khi biết được; NULL cho Semgrep (SARIF không có logicalLocations). */
    private String symbol;

    @Column(nullable = false)
    private String summary;

    /**
     * Payload đặc thù theo loại + metadata riêng của tool không đáng tách cột riêng. Dùng {@code
     * Map} thay vì {@code String} để Hibernate serialize qua Jackson thành JSON object thật, chứ
     * không nhét một chuỗi đã-là-JSON vào rồi bị escape kép.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceStatus status = EvidenceStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
