package com.shop.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.common.util.CorrelationIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Writes a platform {@link ErrorResponse} (401) when a request is unauthenticated or carries an invalid token.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = authException instanceof InvalidBearerTokenException
                ? "Access token is invalid or expired"
                : ErrorCode.UNAUTHORIZED.getDefaultMessage();
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .code(ErrorCode.UNAUTHORIZED.name())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.current())
                .build();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
