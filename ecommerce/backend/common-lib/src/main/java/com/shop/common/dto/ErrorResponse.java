package com.shop.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every service and by the API gateway.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private Instant timestamp = Instant.now();

    /** HTTP status code. */
    private int status;

    /** HTTP reason phrase, e.g. "Not Found". */
    private String error;

    /** Machine-readable error code, see {@link com.shop.common.exception.ErrorCode}. */
    private String code;

    /** Human-readable message, safe to show to clients. */
    private String message;

    /** Request path that produced the error. */
    private String path;

    /** Correlation id of the request, useful when searching logs. */
    private String correlationId;

    /** Field-level validation errors, present only for validation failures. */
    private List<FieldViolation> violations;
}
