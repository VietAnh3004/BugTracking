package vn.hust.datn.evidence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.dto.UserResponse;

// componentModel khai tuong minh, khong chi dua vao co -Amapstruct.defaultComponentModel trong
// pom.xml: background compiler cua IDE khong biet co do va sinh ra *MapperImpl thieu @Component.
// Xem Javadoc ProjectMapper de biet day du trieu chung.
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, imports = Avatars.class)
public interface UserMapper {

    @Mapping(target = "emailVerified", expression = "java(user.getEmailVerifiedAt() != null)")
    @Mapping(target = "hasPassword", expression = "java(user.getPasswordHash() != null)")
    @Mapping(target = "googleLinked", expression = "java(user.getGoogleSubject() != null)")
    @Mapping(target = "avatarUrl", expression = "java(Avatars.urlFor(user))")
    UserResponse toResponse(User user);
}
