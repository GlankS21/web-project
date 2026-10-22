package com.shop.auth.dto.request;

import com.shop.auth.entity.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRolesRequest(

        @Schema(description = "Новый набор ролей аккаунта", example = "[\"ROLE_CUSTOMER\", \"ROLE_ADMIN\"]")
        @NotEmpty
        Set<@NotNull RoleName> roles
) {
}
