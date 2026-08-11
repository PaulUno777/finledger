package com.pauluno.finledger.presentation.advice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.IdempotencyConflictException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> details = Map.of("fieldErrors", fieldErrors);

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request.getRequestURI(),
                details);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Unreadable request body: {}", ex.getMessage());

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                "Request body is required and must be valid JSON",
                request.getRequestURI(),
                null);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Invalid argument: {}", ex.getMessage());

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        log.debug("No handler for {}: {}", request.getMethod(), request.getRequestURI());

        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "No resource found for " + request.getRequestURI(),
                request.getRequestURI(),
                null);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.CONFLICT,
                ex.code(),
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request) {

        HttpStatus status = switch (ex.code()) {
            case "INSUFFICIENT_FUNDS" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "ACCOUNT_CLOSED", "TENANT_ID_CONFLICT" -> HttpStatus.CONFLICT;
            case "WEBHOOK_SIGNATURE_INVALID" -> HttpStatus.UNAUTHORIZED;
            case "WEBHOOK_TIMESTAMP_SKEW", "WEBHOOK_REPLAY" -> HttpStatus.UNAUTHORIZED;
            case "WEBHOOK_SECRET_MISSING" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };

        ErrorResponse response = buildErrorResponse(
                status,
                ex.code(),
                ex.getMessage(),
                request.getRequestURI(),
                null);

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected exception", ex);

        ErrorResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                null);

        return ResponseEntity.internalServerError().body(response);
    }

    private ErrorResponse buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            String path,
            Map<String, Object> details) {

        return new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                MDC.get("traceId"),
                details);
    }
}
