package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một hàng trong chỉ mục file phẳng của một snapshot (V3). FE dựng cây từ danh sách phẳng này
 * (xem {@code SnapshotFileResponse}), nên bảng không mô hình hoá quan hệ cha-con bằng FK — chỉ
 * {@code parentPath} dạng chuỗi, vật chất hoá để liệt kê một mức bằng một truy vấn có index.
 */
@Entity
@Table(name = "snapshot_file")
@Getter
@Setter
@NoArgsConstructor
public class SnapshotFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(nullable = false)
    private String path;

    @Column(name = "parent_path", nullable = false)
    private String parentPath;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private SnapshotEntryType entryType;

    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "line_count")
    private Integer lineCount;

    private String sha256;

    @Column(name = "is_binary", nullable = false)
    private boolean binary;

    @Column(nullable = false)
    private boolean stored = true;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
