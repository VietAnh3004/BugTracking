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
import vn.hust.datn.evidence.domain.ProjectMember;
import vn.hust.datn.evidence.domain.ProjectRole;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.dto.ProjectMemberResponse;
import vn.hust.datn.evidence.exception.InvalidMemberException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.mapper.ProjectMapper;
import vn.hust.datn.evidence.repository.ConversationRepository;
import vn.hust.datn.evidence.repository.ProjectMemberRepository;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.repository.UserRepository;
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
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            RepositorySnapshotRepository snapshotRepository,
            ConversationRepository conversationRepository,
            ObjectStorageClient storageClient,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.snapshotRepository = snapshotRepository;
        this.conversationRepository = conversationRepository;
        this.storageClient = storageClient;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
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

    /** Dự án người dùng sở hữu VÀ dự án họ được chia sẻ (V10). */
    @Transactional(readOnly = true)
    public Page<Project> list(Pageable pageable, UUID userId) {
        return projectRepository.findReadable(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Project get(UUID id, UUID userId) {
        return getReadable(id, userId);
    }

    /** Chủ dự án hoặc thành viên; còn lại ⇒ 404. Cho mọi thao tác ĐỌC trên dự án. */
    @Transactional(readOnly = true)
    public Project getReadable(UUID projectId, UUID userId) {
        return projectRepository
                .findReadableById(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("project", projectId));
    }

    /** Vai trò của người gọi với một dự án ĐÃ biết là họ đọc được. */
    public static ProjectRole roleOf(Project project, UUID userId) {
        return project.getOwnerId().equals(userId) ? ProjectRole.OWNER : ProjectRole.MEMBER;
    }

    /** Thành viên của dự án — chỉ chủ dự án xem được danh sách. */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(UUID projectId, UUID ownerId) {
        getOwned(projectId, ownerId);
        return memberRepository.findByProjectIdOrderByAddedAtAsc(projectId).stream()
                .map(member -> userRepository
                        .findById(member.getUserId())
                        .map(user -> new ProjectMemberResponse(user.getId(), user.getEmail(), user.getDisplayName(), member.getAddedAt()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Mời một người vào dự án bằng email tài khoản của họ. Idempotent: mời lại người đã là thành
     * viên trả về chính hàng đó, không lỗi.
     *
     * <p>Email chưa có tài khoản ⇒ 400 nói thẳng "chưa đăng ký", không 404: người gọi là chủ dự án
     * đang thao tác hợp lệ, cái họ cần biết là bảo người kia đăng ký trước.
     */
    @Transactional
    public ProjectMemberResponse addMember(UUID projectId, UUID ownerId, String email) {
        Project project = getOwned(projectId, ownerId);
        User user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new InvalidMemberException(
                        "Chua co tai khoan nao dang ky bang email nay. Nho nguoi do dang ky truoc roi moi lai."));
        if (user.getId().equals(project.getOwnerId())) {
            throw new InvalidMemberException("Chu du an da co toan quyen, khong can moi lam thanh vien.");
        }

        ProjectMember member = memberRepository
                .findById(new vn.hust.datn.evidence.domain.ProjectMemberId(projectId, user.getId()))
                .orElseGet(() -> {
                    ProjectMember fresh = new ProjectMember();
                    fresh.setProjectId(projectId);
                    fresh.setUserId(user.getId());
                    fresh.setAddedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC)
                            .truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                    return memberRepository.save(fresh);
                });
        return new ProjectMemberResponse(user.getId(), user.getEmail(), user.getDisplayName(), member.getAddedAt());
    }

    /** Gỡ một thành viên. Không phải thành viên thì không làm gì — gỡ hai lần không phải lỗi. */
    @Transactional
    public void removeMember(UUID projectId, UUID ownerId, UUID memberUserId) {
        getOwned(projectId, ownerId);
        memberRepository.deleteById(new vn.hust.datn.evidence.domain.ProjectMemberId(projectId, memberUserId));
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

    /**
     * Chỉ CHỦ dự án — cho thao tác thành viên không được làm: xoá, tải mã, chạy phân tích, quản lý
     * thành viên. Thành viên gọi vào đây cũng nhận 404 như người lạ.
     */
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
