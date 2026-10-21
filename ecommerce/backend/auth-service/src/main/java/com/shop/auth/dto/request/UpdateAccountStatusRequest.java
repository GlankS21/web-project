package com.shop.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(

        @NotNull
        Boolean enabled
) {
}
