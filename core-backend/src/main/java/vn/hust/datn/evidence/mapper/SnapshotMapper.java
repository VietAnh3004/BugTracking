package vn.hust.datn.evidence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.dto.SnapshotResponse;

// componentModel khai tuong minh, khong chi dua vao co -Amapstruct.defaultComponentModel trong
// pom.xml: background compiler cua IDE khong biet co do va sinh ra *MapperImpl thieu @Component.
// Xem Javadoc ProjectMapper de biet day du trieu chung.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SnapshotMapper {

    SnapshotResponse toResponse(RepositorySnapshot snapshot);
}
