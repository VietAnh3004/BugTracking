package vn.hust.datn.evidence.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.dto.SnapshotFileResponse;

// componentModel khai tuong minh, khong chi dua vao co -Amapstruct.defaultComponentModel trong
// pom.xml: background compiler cua IDE khong biet co do va sinh ra *MapperImpl thieu @Component.
// Xem Javadoc ProjectMapper de biet day du trieu chung.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SnapshotFileMapper {

    SnapshotFileResponse toResponse(SnapshotFile file);

    List<SnapshotFileResponse> toResponses(List<SnapshotFile> files);
}
