package vn.hust.datn.evidence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.dto.ProjectResponse;

/**
 * Mapper đầu tiên của dự án — cố ý làm đơn giản nhất có thể (map phẳng 1-1) để chứng minh cấu
 * hình MapStruct + Lombok đúng trước khi dùng cho DTO phức tạp hơn.
 *
 * <p>{@code unmappedTargetPolicy=ERROR} (cấu hình ở pom.xml) buộc phải khai báo tường minh mọi
 * field không map tự động được — {@code id}/{@code createdAt}/{@code updatedAt} của
 * {@link Project} do service gán (sinh UUID, đặt timestamp), không phải mapper.
 */
@Mapper
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Project toEntity(CreateProjectRequest request);
}
