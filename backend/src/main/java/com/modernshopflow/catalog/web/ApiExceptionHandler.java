package com.modernshopflow.catalog.web;

import com.modernshopflow.catalog.api.ApiError;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CatalogNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            CatalogNotFoundException exception, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler({CatalogConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof CatalogConflictException
                ? exception.getMessage()
                : "Request conflicts with existing catalog data";
        return buildErrorResponse(HttpStatus.CONFLICT, "CONFLICT", message, request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "))
                : exception.getMessage();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request);
    }

    private ResponseEntity<ApiError> buildErrorResponse(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status)
                .header("X-Correlation-ID", correlationId)
                .body(new ApiError(
                        OffsetDateTime.now(),
                        status.value(),
                        code,
                        message,
                        request.getRequestURI(),
                        correlationId));
    }
}
