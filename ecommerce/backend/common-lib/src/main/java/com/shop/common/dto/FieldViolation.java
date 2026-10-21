package com.shop.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single validation error on a request field or parameter.
 * The rejected value is intentionally not echoed back to avoid leaking sensitive input.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldViolation {

    private String field;
    private String message;
}
