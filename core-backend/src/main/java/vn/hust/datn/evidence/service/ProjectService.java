package vn.hust.datn.evidence.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.mapper.ProjectMapper;
import vn.hust.datn.evidence.repository.ConversationRepository;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.storage.ObjectStorageException;

/**
 * Quy tắc authorization duy nhất: tài nguyên không thuộc về bạn ⇒ 404 (qua {@link
 * ResourceNotFoundException} đã có sẵn), không phải 403 — không tạo oracle cho phép dò sự tồn
 * tại của project người khác. Mọi method đều lọc theo {@code ownerId} ngay trong câu truy vấn
 * (xem {@link ProjectRepository#findByIdAndOwnerId}), không "tìm rồi so sánh trong Java".
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final RepositorySnapshotRepository snapshotRepository;
    private final ConversationRepository conversationRepository;
    private final ObjectStorageClient storageClient;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            RepositorySnapshotRepository snapshotRepository,
            ConversationRepository conversationRepository,
            ObjectStorageClient storageClient) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.snapshotRepository = snapshotRepository;
        this.conversationRepository = conversationRepository;
        this.storageClient = storageClient;
    }

    @Transactional
    public Project create(CreateProjectRequest request, UUID ownerId) {
        Project project = projectMapper.toEntity(request);
        OffsetDateTime now = OffsetDateTime.now();
        project.setOwnerId(ownerId);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Page<Project> list(Pageable pageable, UUID ownerId) {
        return projectRepository.findByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public Project get(UUID id, UUID ownerId) {
        return getOwned(id, ownerId);
    }

    /**
     * Xoá dự án cùng MỌI thứ bên dưới: snapshot, analysis run, bằng chứng, hội thoại, review.
     * Không thể hoàn tác — kể cả dữ liệu đo của thực nghiệm (claim_citation, human_review) gắn với
     * dự án này; frontend bắt người dùng gõ lại tên dự án trước khi gọi.
     *
     * <p>Hội thoại phải xoá TRƯỚC, tường minh — CASCADE từ project không tự lo được, xem
     * {@link ConversationRepository#deleteByProjectId}. Phần còn lại đi theo ON DELETE CASCADE.
     *
     * <p>Object storage dọn SAU KHI commit, cùng triết lý với {@link SnapshotService}: DB là nguồn
     * sự thật, MinIO được phép có rác mồ côi. Dọn trước rồi DB rollback thì để lại hàng
     * {@code snapshot_file} trỏ vào object đã mất — người dùng thấy lỗi khi đọc file (xấu hơn).
     */
    @Transactional
    public void delete(UUID id, UUID ownerId) {
        Project project = getOwned(id, ownerId);
        List<String> storagePrefixes = snapshotRepository.findStorageKeysByProjectId(id);

        conversationRepository.deleteByProjectId(id);
        projectRepository.delete(project);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStoredObjects(id, storagePrefixes);
            }
        });
    }

    /** API nội bộ cho service khác (vd {@code SnapshotService}, tuần 5-7) cần xác nhận quyền sở hữu trước khi thao tác tiếp. */
    @Transactional(readOnly = true)
    public Project getOwned(UUID projectId, UUID ownerId) {
        return projectRepository
                .findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("project", projectId));
    }

    private void deleteStoredObjects(UUID projectId, List<String> storagePrefixes) {
        for (String prefix : storagePrefixes) {
            // Prefix rỗng (hay không kết thúc bằng '/') mà lọt tới deleteObjectsByPrefix thì xoá
            // sạch bucket, hoặc ăn sang snapshot có id cùng tiền tố. Không bao giờ đáng để thử.
            if (prefix == null || !prefix.startsWith("snapshots/") || !prefix.endsWith("/")) {
                log.warn("Bỏ qua storage prefix bất thường '{}' khi xoá dự án {}", prefix, projectId);
                continue;
            }
            try {
                storageClient.deleteObjectsByPrefix(prefix);
            } catch (ObjectStorageException e) {
                // DB đã commit — không còn gì để rollback; object mồ côi chỉ tốn dung lượng.
                log.warn("Dự án {} đã xoá nhưng không dọn được object storage '{}'", projectId, prefix, e);
            }
        }
    }
}
