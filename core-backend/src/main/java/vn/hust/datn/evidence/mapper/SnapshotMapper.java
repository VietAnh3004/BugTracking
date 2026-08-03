package vn.hust.datn.evidence.mapper;

import org.mapstruct.Mapper;

import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.dto.SnapshotResponse;

@Mapper
public interface SnapshotMapper {

    SnapshotResponse toResponse(RepositorySnapshot snapshot);
}
