package com.pgcluster.api.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiException")
class ApiExceptionTest {

    @Test
    @DisplayName("should create exception with message and status")
    void shouldCreateWithMessageAndStatus() {
        ApiException ex = new ApiException("Not found", HttpStatus.NOT_FOUND);

        assertThat(ex.getMessage()).isEqualTo("Not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should create exception with message, status, and cause")
    void shouldCreateWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ApiException ex = new ApiException("Server error", HttpStatus.INTERNAL_SERVER_ERROR, cause);

        assertThat(ex.getMessage()).isEqualTo("Server error");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("should extend RuntimeException")
    void shouldExtendRuntimeException() {
        ApiException ex = new ApiException("test", HttpStatus.BAD_REQUEST);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
