package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
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
 * Một người được chủ dự án mời vào dự án (V10). Chủ dự án KHÔNG có hàng ở đây — vẫn là
 * {@link Project#getOwnerId()}.
 *
 * <p>UUID trần, không quan hệ JPA, cùng khuôn với mọi entity khác của dự án: truy vấn phân quyền
 * viết bằng JPQL tường minh, không dựa vào navigation property (xem {@code ProjectRepository}).
 */
@Entity
@Table(name = "project_member")
@IdClass(ProjectMemberId.class)
@Getter
@Setter
@NoArgsConstructor
public class ProjectMember {

    @Id
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;
}
