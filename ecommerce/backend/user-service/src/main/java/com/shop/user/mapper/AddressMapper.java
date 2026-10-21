package com.shop.user.mapper;

import com.shop.user.dto.request.AddressRequest;
import com.shop.user.dto.response.AddressResponse;
import com.shop.user.entity.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * The default flag and the owner are managed by the service, never copied from the request.
 */
@Mapper
public interface AddressMapper {

    AddressResponse toResponse(Address address);

    List<AddressResponse> toResponses(List<Address> addresses);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "defaultAddress", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Address toEntity(AddressRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "defaultAddress", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(AddressRequest request, @MappingTarget Address address);
}
