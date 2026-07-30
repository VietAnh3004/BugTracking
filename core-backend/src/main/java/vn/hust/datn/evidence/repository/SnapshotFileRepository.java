package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotFile;

public interface SnapshotFileRepository extends JpaRepository<SnapshotFile, UUID> {

    Optional<SnapshotFile> findBySnapshotIdAndPath(UUID snapshotId, String path);

    // Sắp thư mục trước file rồi theo tên — khớp idx_snapshot_file_dir; dùng cho FE dựng cây
    // theo danh sách phẳng của toàn bộ snapshot.
    List<SnapshotFile> findBySnapshotIdOrderByEntryTypeDescNameAsc(UUID snapshotId);

    long countBySnapshotId(UUID snapshotId);

    boolean existsBySnapshotIdAndEntryType(UUID snapshotId, SnapshotEntryType entryType);
}
