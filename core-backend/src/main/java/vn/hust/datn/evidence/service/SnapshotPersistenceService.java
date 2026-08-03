package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.domain.SnapshotSourceType;
import vn.hust.datn.evidence.domain.SnapshotStatus;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;

/**
 * Ba thao tác DB tách riêng khỏi {@link SnapshotService}, MỖI THAO TÁC MỘT TRANSACTION RIÊNG —
 * đây KHÔNG phải chia lớp cho gọn, mà là điều kiện bắt buộc để propagation hoạt động đúng.
 *
 * <p>Nếu ba method này nằm chung class với {@code SnapshotService.upload()} và được gọi qua
 * {@code this.createPending(...)}, lời gọi đó là self-invocation — KHÔNG đi qua Spring AOP proxy,
 * nên {@code @Transactional(propagation = REQUIRES_NEW)} bị bỏ qua hoàn toàn (không lỗi, không
 * cảnh báo, chỉ lặng lẽ chạy trong transaction hiện có, hoặc không transaction nào nếu gọi từ
 * method không transaction). Việc tách class buộc lời gọi phải đi qua bean injected → qua proxy →
 * propagation áp dụng đúng.
 */
@Service
public class SnapshotPersistenceService {

    private final RepositorySnapshotRepository snapshotRepository;
    private final SnapshotFileRepository snapshotFileRepository;

    public SnapshotPersistenceService(
            RepositorySnapshotRepository snapshotRepository, SnapshotFileRepository snapshotFileRepository) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotFileRepository = snapshotFileRepository;
    }

    /**
     * Bia mộ: hàng {@code PENDING} phải COMMIT NGAY, độc lập với việc giải nén/upload phía sau có
     * thành công hay không — nếu không, một crash giữa chừng sẽ không để lại dấu vết gì trong DB.
     *
     * <p>{@code storage_key} là tiền tố object storage {@code snapshots/{id}/} — chỉ biết được sau
     * khi {@code id} đã sinh, nên lưu hai lượt trong cùng transaction (một lần flush để lấy id do
     * {@code GenerationType.UUID} sinh, một lần gán lại storage_key) thay vì tự sinh UUID phía Java
     * và gán tay (sẽ phải tắt {@code @GeneratedValue}, đổi chiến lược của toàn entity).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositorySnapshot createPending(UUID projectId, String sourceRef, SnapshotSourceType sourceType) {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.setProjectId(projectId);
        snapshot.setSourceRef(sourceRef);
        snapshot.setSourceType(sourceType);
        snapshot.setStatus(SnapshotStatus.PENDING);
        snapshot.setCreatedAt(OffsetDateTime.now());
        snapshot.setStorageKey("pending");
        snapshot = snapshotRepository.saveAndFlush(snapshot);
        snapshot.setStorageKey("snapshots/" + snapshot.getId() + "/");
        return snapshot;
    }

    /** Transaction cuối, ngắn: chỉ ghi hàng đã có sẵn trong bộ nhớ, object storage đã ghi xong từ trước. */
    @Transactional
    public RepositorySnapshot finalizeReady(UUID snapshotId, List<SnapshotFile> files, int fileCount, long totalSizeBytes) {
        snapshotFileRepository.saveAll(files);
        RepositorySnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
        snapshot.setStatus(SnapshotStatus.READY);
        snapshot.setFileCount(fileCount);
        snapshot.setTotalSizeBytes(totalSizeBytes);
        return snapshot;
    }

    /** REQUIRES_NEW: gọi từ nhánh catch của {@code SnapshotService.upload()} khi KHÔNG có transaction nào đang mở. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID snapshotId, String errorMessage) {
        RepositorySnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
        snapshot.setStatus(SnapshotStatus.FAILED);
        snapshot.setErrorMessage(errorMessage);
    }
}
