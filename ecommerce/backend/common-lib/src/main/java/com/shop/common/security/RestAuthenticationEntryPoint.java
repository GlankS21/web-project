package com.shop.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.common.util.CorrelationIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Writes a platform {@link ErrorResponse} (401) when a request is unauthenticated or carries an invalid token.
 * The message is resolved in {@link CommonMessagesAutoConfiguration#DEFAULT_LOCALE}: security filters run
 * before the dispatcher sets up the request locale.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String INVALID_TOKEN_KEY = "error.token.invalid";

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = authException instanceof InvalidBearerTokenException
                ? messageSource.getMessage(INVALID_TOKEN_KEY, null, "Access token is invalid or expired",
                        CommonMessagesAutoConfiguration.DEFAULT_LOCALE)
                : messageSource.getMessage("error." + ErrorCode.UNAUTHORIZED.name(), null,
                        ErrorCode.UNAUTHORIZED.getDefaultMessage(), CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
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
