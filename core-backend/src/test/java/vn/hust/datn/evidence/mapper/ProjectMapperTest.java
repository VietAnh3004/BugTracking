package vn.hust.datn.evidence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.dto.ProjectResponse;

/**
 * Mapper đầu tiên của dự án dùng chung Lombok + MapStruct — pom.xml thiếu
 * {@code lombok-mapstruct-binding} sẽ khiến MapStruct chạy trước khi Lombok sinh getter, sinh ra
 * implementation RỖNG mà vẫn biên dịch thành công (không báo lỗi gì). Test này assert TỪNG field
 * non-null/đúng giá trị — "pom đã sửa" không phải bằng chứng, chỉ có test này mới là bằng chứng.
 *
 * <p>Không cần Spring context: {@code ProjectMapperImpl} là POJO thường (annotation
 * {@code @Component} chỉ có ý nghĩa khi Spring quét, không cản việc {@code new} trực tiếp).
 */
class ProjectMapperTest {

    private final ProjectMapper mapper = new ProjectMapperImpl();

    @Test
    void toResponse_khongDeCotNaoBiRong() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("vulnshop");
        project.setDescription("Case study có gieo lỗi chủ đích");
        project.setCreatedAt(OffsetDateTime.now());
        project.setUpdatedAt(OffsetDateTime.now());

        ProjectResponse response = mapper.toResponse(project);

        assertThat(response.id()).isEqualTo(project.getId());
        assertThat(response.name()).isEqualTo("vulnshop");
        assertThat(response.description()).isEqualTo("Case study có gieo lỗi chủ đích");
        assertThat(response.createdAt()).isEqualTo(project.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(project.getUpdatedAt());
    }

    @Test
    void toEntity_mapDungHaiTruongVaBoQuaBaTruongDoServiceGan() {
        CreateProjectRequest request = new CreateProjectRequest("vulnshop", "mô tả");

        Project project = mapper.toEntity(request);

        assertThat(project.getName()).isEqualTo("vulnshop");
        assertThat(project.getDescription()).isEqualTo("mô tả");
        assertThat(project.getId()).isNull();
        assertThat(project.getCreatedAt()).isNull();
        assertThat(project.getUpdatedAt()).isNull();
    }
}
