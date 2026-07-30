package vn.hust.datn.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.repository.ProjectRepository;

/**
 * Bước 1 của tuần 3: đúng MỘT entity, map vào bảng {@code project} đã tồn tại từ V1.
 *
 * <p>Nếu đỏ, nguyên nhân chắc chắn là kỹ năng mapping (UUID PK, TIMESTAMPTZ -> OffsetDateTime,
 * naming strategy camelCase -> snake_case) — không thể lẫn với lỗi thiết kế schema V2, vì V2
 * chưa được entity nào đụng tới ở test này.
 */
class ProjectEntityTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void luuVaDocLaiDuocMotProject() {
        Project project = new Project();
        project.setName("vulnshop");
        project.setDescription("Case study có gieo lỗi chủ đích");
        project.setCreatedAt(OffsetDateTime.now());
        project.setUpdatedAt(OffsetDateTime.now());

        Project saved = projectRepository.save(project);

        assertThat(saved.getId()).isNotNull();
        Project reloaded = projectRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("vulnshop");
    }
}
