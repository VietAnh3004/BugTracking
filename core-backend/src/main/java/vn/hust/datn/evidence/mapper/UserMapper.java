package vn.hust.datn.evidence.mapper;

import org.mapstruct.Mapper;

import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.dto.UserResponse;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
