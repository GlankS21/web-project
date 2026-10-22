package com.shop.common.web;

import com.shop.common.dto.ErrorResponse;
import com.shop.common.dto.FieldViolation;
import com.shop.common.exception.BaseException;
import com.shop.common.exception.ErrorCode;
import com.shop.common.util.CorrelationIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates exceptions into the platform {@link ErrorResponse} format.
 * <p>
 * Each servlet-based service declares its own {@code GlobalExceptionHandler} annotated with
 * {@code @RestControllerAdvice} that extends this class and adds service-specific handlers
 * (Feign...). Requires spring-web, spring-security, spring-data and jakarta validation on the classpath.
 * <p>
 * Messages are resolved through the {@link MessageSource} for the current locale (ru-RU by default):
 * a {@link BaseException} message is a key with arguments, other errors use the key of their
 * {@link ErrorCode}. Text that is not a key is returned unchanged.
 */
@Slf4j
public abstract class AbstractGlobalExceptionHandler {

    private MessageSource messageSource;

    @Autowired(required = false)
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.getHttpStatus() >= 500) {
            log.error("Request {} {} failed: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("Request {} {} rejected [{}]: {}", request.getMethod(), request.getRequestURI(), code, ex.getMessage());
        }
        return buildResponse(code, localize(ex.getMessage(), ex.getMessageArgs()), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                violations.add(new FieldViolation(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return buildResponse(ErrorCode.VALIDATION_FAILED, defaultMessage(ErrorCode.VALIDATION_FAILED),
                request, violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                       HttpServletRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                String field = error instanceof FieldError fieldError ? fieldError.getField() : parameterName;
                violations.add(new FieldViolation(field, error.getDefaultMessage()));
            });
        });
        return buildResponse(ErrorCode.VALIDATION_FAILED, defaultMessage(ErrorCode.VALIDATION_FAILED),
                request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return buildResponse(ErrorCode.VALIDATION_FAILED, defaultMessage(ErrorCode.VALIDATION_FAILED),
                request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST,
                message("error.request.unreadable", "Malformed or unreadable request body"), request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST,
                message("error.request.missing-parameter", "Required parameter '%s' is missing",
                        ex.getParameterName()), request, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST,
                message("error.request.missing-header", "Required header '%s' is missing", ex.getHeaderName()),
                request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST,
                message("error.request.invalid-parameter", "Parameter '%s' has an invalid value", ex.getName()),
                request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED,
                message("error.request.method-not-supported", "Method '%s' is not supported for this endpoint",
                        ex.getMethod()), request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return buildResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                defaultMessage(ErrorCode.UNSUPPORTED_MEDIA_TYPE), request, null);
    }

    /**
     * Thrown by method security ({@code @PreAuthorize}) inside the controller layer, i.e. after the
     * security filter chain, so it must be translated here rather than by the access denied handler.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.FORBIDDEN, defaultMessage(ErrorCode.FORBIDDEN), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.UNAUTHORIZED, defaultMessage(ErrorCode.UNAUTHORIZED), request, null);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSort(PropertyReferenceException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST,
                message("error.request.unknown-sort", "Unknown sort property '%s'", ex.getPropertyName()),
                request, null);
    }

    /**
     * Last line of defence for unique/foreign key constraints (e.g. two concurrent registrations
     * with the same email). The database message is logged, never returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return buildResponse(ErrorCode.CONFLICT,
                message("error.data.conflict", "The request conflicts with existing data"), request, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        return buildResponse(ErrorCode.CONFLICT,
                message("error.data.concurrent-modification",
                        "The resource was modified concurrently, please reload and retry"), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.RESOURCE_NOT_FOUND,
                message("error.request.endpoint-not-found", "Endpoint not found"), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR, defaultMessage(ErrorCode.INTERNAL_ERROR), request, null);
    }

    /**
     * @return the message of {@code key} in the current locale, or {@code key} itself when unknown
     */
    protected String localize(String key, Object... args) {
        if (messageSource == null || key == null) {
            return key;
        }
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    /**
     * @param fallback English text with {@code %s} placeholders, used when the key is not translated
     */
    protected String message(String key, String fallback, Object... args) {
        String message = localize(key, args);
        return key.equals(message) ? fallback.formatted(args) : message;
    }

    protected String defaultMessage(ErrorCode code) {
        return message(ErrorCode.messageKeyOf(code), code.getDefaultMessage());
    }

    protected ResponseEntity<ErrorResponse> buildResponse(ErrorCode code,
                                                          String message,
                                                          HttpServletRequest request,
                                                          List<FieldViolation> violations) {
        HttpStatus status = HttpStatus.valueOf(code.getHttpStatus());
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code.name())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(CorrelationIdUtils.current())
                .violations(violations == null || violations.isEmpty() ? null : violations)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
