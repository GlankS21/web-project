package com.shop.user.mapper;

import com.shop.user.dto.request.UpdateProfileRequest;
import com.shop.user.dto.response.UserProfileResponse;
import com.shop.user.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface UserProfileMapper {

    UserProfileResponse toResponse(UserProfile profile);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(UpdateProfileRequest request, @MappingTarget UserProfile profile);
}
