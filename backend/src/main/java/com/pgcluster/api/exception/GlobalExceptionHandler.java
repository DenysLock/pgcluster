package com.pgcluster.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        log.error("API Exception: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", "Invalid request body");
        body.put("errors", errors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException ex) {
        log.error("IO error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "External service communication error");
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleRestClientException(RestClientException ex) {
        log.error("REST client error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_GATEWAY,
                "External API error");
    }

    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<Map<String, Object>> handleInterruptedException(InterruptedException ex) {
        Thread.currentThread().interrupt();
        log.error("Operation interrupted: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Operation was interrupted");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PitrValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePitrValidationException(PitrValidationException ex) {
        log.warn("PITR validation failed: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("error", HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("code", ex.getCode());
        body.put("requestedTargetTime", ex.getRequestedTargetTime() != null ? ex.getRequestedTargetTime().toString() : null);
        body.put("nearestBefore", ex.getNearestBefore() != null ? ex.getNearestBefore().toString() : null);
        body.put("nearestAfter", ex.getNearestAfter() != null ? ex.getNearestAfter().toString() : null);
        body.put("earliestPitrTime", ex.getEarliestPitrTime() != null ? ex.getEarliestPitrTime().toString() : null);
        body.put("latestPitrTime", ex.getLatestPitrTime() != null ? ex.getLatestPitrTime().toString() : null);

        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("Invalid state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
