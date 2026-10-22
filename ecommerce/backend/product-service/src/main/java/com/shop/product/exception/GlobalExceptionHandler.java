package com.shop.product.exception;

import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.common.web.AbstractGlobalExceptionHandler;
import com.shop.product.config.CatalogProperties;
import com.shop.product.util.SizeFormatter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Uses the platform-wide translation of exceptions into {@code ErrorResponse}
 * (validation, business, security and persistence errors), plus the multipart errors of the
 * image upload, which are raised before the controller is reached.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    private final CatalogProperties catalogProperties;

    /**
     * The upload is larger than {@code spring.servlet.multipart.max-file-size}; the limit reported is
     * the one of the application, which the multipart limit only slightly exceeds.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                             HttpServletRequest request) {
        log.warn("Request {} {} rejected: upload larger than {} bytes", request.getMethod(),
                request.getRequestURI(), ex.getMaxUploadSize());
        return buildResponse(ErrorCode.BAD_REQUEST,
                localize("image.error.too-large", SizeFormatter.format(catalogProperties.imageMaxSize())),
                request, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex, HttpServletRequest request) {
        log.warn("Request {} {} rejected: unreadable multipart body: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage());
        return buildResponse(ErrorCode.BAD_REQUEST, defaultMessage(ErrorCode.BAD_REQUEST), request, null);
    }
}
