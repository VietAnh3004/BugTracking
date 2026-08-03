package vn.hust.datn.evidence.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.dto.SnapshotFileResponse;

@Mapper
public interface SnapshotFileMapper {

    SnapshotFileResponse toResponse(SnapshotFile file);

    List<SnapshotFileResponse> toResponses(List<SnapshotFile> files);
}
