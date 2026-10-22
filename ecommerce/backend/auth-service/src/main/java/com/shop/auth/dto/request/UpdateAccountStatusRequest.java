package com.shop.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(

        @Schema(description = "true - аккаунт активен, false - заблокирован", example = "false")
        @NotNull
        Boolean enabled
) {
}
