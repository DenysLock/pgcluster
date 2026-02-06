package com.pgcluster.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("AsyncConfig")
class AsyncConfigTest {

    @Nested
    @DisplayName("getAsyncExecutor")
    class GetAsyncExecutor {

        @Test
        @DisplayName("should create and configure thread pool executor")
        void shouldCreateExecutor() {
            AsyncConfig config = new AsyncConfig();
            Executor executor = config.getAsyncExecutor();

            assertThat(executor).isNotNull();
        }
    }

    @Nested
    @DisplayName("CustomAsyncExceptionHandler")
    class CustomAsyncExceptionHandlerTest {

        @Test
        @DisplayName("should handle uncaught exception without throwing")
        void shouldHandleException() throws NoSuchMethodException {
            AsyncConfig config = new AsyncConfig();
            AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

            Method testMethod = String.class.getMethod("toString");

            assertThat(handler).isNotNull();
            assertThatNoException().isThrownBy(() ->
                    handler.handleUncaughtException(
                            new RuntimeException("Test error"),
                            testMethod,
                            "param1", "param2"
                    ));
        }

        @Test
        @DisplayName("should handle exception with no parameters")
        void shouldHandleExceptionNoParams() throws NoSuchMethodException {
            AsyncConfig config = new AsyncConfig();
            AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

            Method testMethod = String.class.getMethod("toString");

            assertThatNoException().isThrownBy(() ->
                    handler.handleUncaughtException(
                            new RuntimeException("Test error"),
                            testMethod
                    ));
        }

        @Test
        @DisplayName("should handle exception with null parameter")
        void shouldHandleExceptionWithNullParam() throws NoSuchMethodException {
            AsyncConfig config = new AsyncConfig();
            AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

            Method testMethod = String.class.getMethod("toString");

            assertThatNoException().isThrownBy(() ->
                    handler.handleUncaughtException(
                            new RuntimeException("Test error"),
                            testMethod,
                            (Object) null
                    ));
        }
    }
}
