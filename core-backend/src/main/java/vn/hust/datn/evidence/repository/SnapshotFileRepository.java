package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotFile;

public interface SnapshotFileRepository extends JpaRepository<SnapshotFile, UUID> {

    Optional<SnapshotFile> findBySnapshotIdAndPath(UUID snapshotId, String path);

    // Sắp thư mục trước file rồi theo tên — khớp idx_snapshot_file_dir; dùng cho FE dựng cây
    // theo danh sách phẳng của toàn bộ snapshot.
    List<SnapshotFile> findBySnapshotIdOrderByEntryTypeDescNameAsc(UUID snapshotId);

    // Sắp theo path cho API /files: FE tự dựng cây từ danh sách PHẲNG (xem ADR/kế hoạch tuần 5-7),
    // và thứ tự theo path giữ mọi node cùng thư mục nằm liền nhau — dựng cây một lượt, không phải
    // sắp lại phía client.
    List<SnapshotFile> findBySnapshotIdOrderByPathAsc(UUID snapshotId);

    /**
     * Chỉ lấy cột path, không nạp entity: dùng để dựng {@code SnapshotPathResolver} một lần cho
     * cả run. Trần 20.000 file ({@code UPLOAD_MAX_FILES}) nên vài MB chuỗi, còn nạp đủ entity thì
     * kéo theo cả sha256/storage_key/line_count vô ích.
     */
    @Query("select f.path from SnapshotFile f where f.snapshotId = :snapshotId and f.entryType = 'FILE'")
    List<String> findFilePathsBySnapshotId(@Param("snapshotId") UUID snapshotId);

    long countBySnapshotId(UUID snapshotId);

    boolean existsBySnapshotIdAndEntryType(UUID snapshotId, SnapshotEntryType entryType);
}
