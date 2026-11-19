package vn.hust.datn.evidence.service.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.dto.AdminUserView;
import vn.hust.datn.evidence.exception.AdminActionNotAllowedException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.repository.UserRepository;
import vn.hust.datn.evidence.service.ProjectService;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý tài khoản dành cho admin: liệt kê và xoá.
 *
 * <h2>Xoá một tài khoản là xoá những gì</h2>
 *
 * <ol>
 *   <li>MỌI dự án tài khoản SỞ HỮU, qua đúng đường xoá dự án của người dùng
 *       ({@link ProjectService#deleteWithContents}): snapshot, analysis run, bằng chứng, hội thoại của
 *       MỌI người trong các dự án đó, review, và file trên object storage (dọn sau commit).
 *       Bước này phải đi TRƯỚC vì {@code project.owner_id} là {@code ON DELETE RESTRICT}.
 *   <li>Hàng {@code app_user}. Phần còn lại đi theo {@code ON DELETE CASCADE} sẵn có: hội thoại và
 *       review của họ trên dự án CỦA NGƯỜI KHÁC, tư cách thành viên, refresh token, token tài khoản.
 * </ol>
 *
 * <p>Nhãn ground truth người-gán-thứ-hai ở dự án người khác được GIỮ: nó thuộc dữ liệu thực nghiệm
 * của chủ dự án, và bảng chỉ lưu tên hiển thị dạng chữ (không khoá ngoại tới tài khoản) nên cũng
 * không có cách xác định chắc chắn nhãn nào của ai.
 *
 * <p>Rủi ro chấp nhận: access token của người bị xoá còn hiệu lực tối đa 15 phút (JWT stateless),
 * nhưng refresh token mất ngay và mọi tài nguyên của họ đã biến mất, nên trong khoảng đó họ chỉ
 * nhận 404.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    // Tham số luôn đi qua bind variable, không bao giờ nối chuỗi vào câu lệnh.
    private static final String LIST_SQL = """
            SELECT u.id, u.email, u.display_name, u.role,
                   (u.email_verified_at IS NOT NULL) AS email_verified,
                   (u.password_hash IS NOT NULL)     AS has_password,
                   (u.google_subject IS NOT NULL)    AS google_linked,
                   u.created_at,
                   (SELECT count(*) FROM project p WHERE p.owner_id = u.id)      AS owned_projects,
                   (SELECT count(*) FROM conversation c WHERE c.owner_id = u.id) AS conversations
            FROM app_user u
            WHERE (?::text IS NULL OR lower(u.email) LIKE ? OR lower(u.display_name) LIKE ?)
            ORDER BY u.created_at DESC, u.id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    @Transactional(readOnly = true)
    public List<AdminUserView> list(String query) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbcTemplate.query(
                LIST_SQL,
                (rs, rowNum) -> new AdminUserView(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getBoolean("email_verified"),
                        rs.getBoolean("has_password"),
                        rs.getBoolean("google_linked"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getInt("owned_projects"),
                        rs.getInt("conversations")),
                pattern,
                pattern,
                pattern);
    }

    @Transactional
    public void delete(UUID targetId, UUID actingAdminId) {
        User target =
                userRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("user", targetId));
        if (target.getId().equals(actingAdminId)) {
            throw new AdminActionNotAllowedException("Không thể tự xoá tài khoản đang đăng nhập");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new AdminActionNotAllowedException("Không thể xoá tài khoản quản trị");
        }

        List<Project> owned = projectRepository.findAllByOwnerId(targetId);
        for (Project project : owned) {
            projectService.deleteWithContents(project);
        }
        // flush: lệnh DELETE dự án phải chạy xong trước khi xoá user, nếu không Hibernate có thể sắp
        // DELETE app_user lên trước và vấp ON DELETE RESTRICT của project.owner_id.
        projectRepository.flush();
        userRepository.delete(target);

        // Chỉ id, không ghi email: log không nên trở thành nơi lưu danh sách người dùng đã xoá.
        log.info("Admin {} đã xoá tài khoản {} cùng {} dự án", actingAdminId, targetId, owned.size());
    }
}
