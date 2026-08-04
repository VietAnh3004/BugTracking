package vn.hust.datn.evidence.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một vị trí của cảnh báo tĩnh. Quan hệ 1:N với {@link Warning} — KHÔNG theo khuôn 1:1 như
 * warning, vì 3/28 BugInstance thật có 2-3 vị trí (một {@code UNSAFE_HASH_EQUALS} trỏ tới 3 file
 * khác nhau). Xem V5__evidence_raw_tables.sql cho lý do đầy đủ.
 *
 * <p>Entity này phục vụ ĐƯỜNG ĐỌC. Đường ghi đi qua {@code EvidenceMaterializer} bằng
 * {@code JdbcTemplate.batchUpdate} — bất đối xứng có chủ đích, xem Javadoc của lớp đó.
 */
@Entity
@Table(name = "warning_location")
@IdClass(WarningLocationId.class)
@Getter
@Setter
@NoArgsConstructor
public class WarningLocation {

    @Id
    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Id
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    /**
     * {@code SourceLine/@role} nguyên văn của SpotBugs. Cố tình KHÔNG enum hoá: detector mới sinh
     * role mới, và một CHECK ở đây sẽ làm hỏng cả run chỉ vì một chuỗi vô hại.
     */
    @Column(name = "role_raw")
    private String roleRaw;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    /** Đường dẫn nguyên văn tool khai — giữ để reparse được khi chuẩn hoá sai. */
    @Column(name = "path_raw", nullable = false)
    private String pathRaw;

    /** Đã chuẩn hoá về {@code snapshot_file.path}; NULL = không đối chiếu được. */
    @Column(name = "file_path")
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "class_name")
    private String className;

    @Column(name = "method_name")
    private String methodName;
}
