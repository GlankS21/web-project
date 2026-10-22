package com.shop.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.common.util.CorrelationIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Writes a platform {@link ErrorResponse} (403) when an authenticated caller lacks the required role.
 * The message is resolved in {@link CommonMessagesAutoConfiguration#DEFAULT_LOCALE}.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .code(ErrorCode.FORBIDDEN.name())
                .message(messageSource.getMessage("error." + ErrorCode.FORBIDDEN.name(), null,
                        ErrorCode.FORBIDDEN.getDefaultMessage(), CommonMessagesAutoConfiguration.DEFAULT_LOCALE))
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.current())
                .build();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
