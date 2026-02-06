package com.pgcluster.api.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("should handle ApiException with correct status and message")
    void shouldHandleApiException() {
        ApiException ex = new ApiException("Resource not found", HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Resource not found");
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    @DisplayName("should handle MethodArgumentNotValidException with field errors")
    void shouldHandleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "must not be blank");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Invalid request body");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertThat(errors).containsEntry("email", "must not be blank");
    }

    @Test
    @DisplayName("should handle IOException as SERVICE_UNAVAILABLE")
    void shouldHandleIOException() {
        IOException ex = new IOException("Connection refused");

        ResponseEntity<Map<String, Object>> response = handler.handleIOException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("message", "External service communication error");
    }

    @Test
    @DisplayName("should handle RestClientException as BAD_GATEWAY")
    void shouldHandleRestClientException() {
        RestClientException ex = new RestClientException("Connection timeout");

        ResponseEntity<Map<String, Object>> response = handler.handleRestClientException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("message", "External API error");
    }

    @Test
    @DisplayName("should handle InterruptedException as SERVICE_UNAVAILABLE")
    void shouldHandleInterruptedException() {
        InterruptedException ex = new InterruptedException("interrupted");

        ResponseEntity<Map<String, Object>> response = handler.handleInterruptedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("message", "Operation was interrupted");
    }

    @Test
    @DisplayName("should handle IllegalArgumentException as BAD_REQUEST")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Invalid input");
    }

    @Test
    @DisplayName("should handle IllegalStateException as CONFLICT")
    void shouldHandleIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Conflicting state");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalStateException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Conflicting state");
    }

    @Test
    @DisplayName("should handle generic Exception as INTERNAL_SERVER_ERROR")
    void shouldHandleGenericException() {
        Exception ex = new Exception("Unexpected");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "An unexpected error occurred");
    }
}
