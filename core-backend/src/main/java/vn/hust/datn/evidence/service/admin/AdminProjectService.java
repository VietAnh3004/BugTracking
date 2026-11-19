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
import vn.hust.datn.evidence.domain.ProjectTier;
import vn.hust.datn.evidence.dto.AdminProjectView;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý dự án dành cho admin: xem và đổi hạng Free/Premium.
 *
 * <p>Hạng được đọc lại ở MỖI câu hỏi gửi Agent ({@code ConversationService.ask}), nên hạ một dự án
 * về Free có hiệu lực ngay từ câu hỏi kế tiếp — không có cache nào giữ quyền cũ.
 */
@Service
@RequiredArgsConstructor
public class AdminProjectService {

    private static final Logger log = LoggerFactory.getLogger(AdminProjectService.class);

    // Tham số luôn đi qua bind variable, không bao giờ nối chuỗi vào câu lệnh. Đếm bằng truy vấn
    // con tương quan: quy mô đồ án, và tránh nhân dòng khi join nhiều bảng một-nhiều cùng lúc.
    private static final String SELECT_SQL = """
            SELECT p.id, p.name, p.tier, p.created_at,
                   u.id AS owner_id, u.email AS owner_email, u.display_name AS owner_name,
                   (SELECT count(*) FROM project_member m WHERE m.project_id = p.id)       AS members,
                   (SELECT count(*) FROM repository_snapshot s WHERE s.project_id = p.id)  AS snapshots,
                   (SELECT count(*) FROM conversation c
                      JOIN analysis_run r ON r.id = c.analysis_run_id
                      JOIN repository_snapshot s2 ON s2.id = r.snapshot_id
                     WHERE s2.project_id = p.id)                                           AS conversations
            FROM project p
            JOIN app_user u ON u.id = p.owner_id
            """;

    private static final String LIST_SQL = SELECT_SQL + """
            WHERE (?::text IS NULL OR lower(p.name) LIKE ? OR lower(u.email) LIKE ?)
            ORDER BY p.created_at DESC, p.id
            """;

    private static final String ONE_SQL = SELECT_SQL + " WHERE p.id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<AdminProjectView> list(String query) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> toView(rs), pattern, pattern, pattern);
    }

    @Transactional
    public AdminProjectView changeTier(UUID projectId, ProjectTier tier, UUID actingAdminId) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("project", projectId));
        ProjectTier before = project.getTier();
        if (before != tier) {
            project.setTier(tier);
            project.setUpdatedAt(OffsetDateTime.now());
            projectRepository.saveAndFlush(project);
            // Chỉ id: log không nên thành nơi lưu tên dự án hay email của người dùng.
            log.info("Admin {} đổi hạng dự án {}: {} -> {}", actingAdminId, projectId, before, tier);
        }
        return jdbcTemplate.queryForObject(ONE_SQL, (rs, rowNum) -> toView(rs), projectId);
    }

    private static AdminProjectView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminProjectView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                ProjectTier.valueOf(rs.getString("tier")),
                rs.getObject("owner_id", UUID.class),
                rs.getString("owner_email"),
                rs.getString("owner_name"),
                rs.getInt("members"),
                rs.getInt("snapshots"),
                rs.getInt("conversations"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
