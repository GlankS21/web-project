package com.shop.auth.dto.request;

import com.shop.auth.entity.RoleName;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRolesRequest(

        @NotEmpty
        Set<@NotNull RoleName> roles
) {
}
