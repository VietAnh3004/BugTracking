package vn.hust.datn.evidence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

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
 *
 * <p>{@code componentModel} khai TƯỜNG MINH ở đây chứ không chỉ dựa vào cờ
 * {@code -Amapstruct.defaultComponentModel=spring} trong pom.xml: cờ đó chỉ tồn tại trong tiến
 * trình Maven, còn background compiler của IDE (Eclipse JDT) cũng ghi vào cùng thư mục
 * {@code target/generated-sources/} và không hề biết tới nó — kết quả là một {@code *MapperImpl}
 * KHÔNG có {@code @Component}, và ứng dụng chết lúc khởi động với "No qualifying bean of type
 * XMapper" dù code hoàn toàn đúng. Khai ở annotation thì cả hai trình biên dịch cùng sinh đúng.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Project toEntity(CreateProjectRequest request);
}
