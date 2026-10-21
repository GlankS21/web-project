package com.shop.auth.mapper;

import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.entity.Role;
import com.shop.auth.entity.UserAccount;
import org.mapstruct.Mapper;

@Mapper
public interface AccountMapper {

    AccountResponse toResponse(UserAccount account);

    default String toRoleName(Role role) {
        return role.getName().name();
    }
}
