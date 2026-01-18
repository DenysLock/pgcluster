package com.pgcluster.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for async task execution.
 * Provides a properly sized thread pool for cluster provisioning operations.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: number of threads to keep alive even when idle
        executor.setCorePoolSize(4);

        // Max pool size: maximum number of threads for peak load
        executor.setMaxPoolSize(10);

        // Queue capacity: number of tasks to queue before creating new threads up to max
        executor.setQueueCapacity(50);

        // Thread name prefix for easy identification in logs and debugging
        executor.setThreadNamePrefix("cluster-provisioning-");

        // Keep alive time for excess threads (seconds)
        executor.setKeepAliveSeconds(60);

        // Allow core threads to time out and die if idle
        executor.setAllowCoreThreadTimeOut(true);

        // Rejection policy: run in caller's thread if queue is full and max threads reached
        // This provides backpressure to the caller rather than losing tasks
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Async executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * Custom exception handler for async methods.
     * Ensures exceptions in async tasks are properly logged.
     */
    private static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Async method {} threw exception: {}",
                    method.getName(),
                    ex.getMessage(),
                    ex);

            // Log parameters for debugging
            if (params.length > 0) {
                StringBuilder paramInfo = new StringBuilder("Parameters: ");
                for (int i = 0; i < params.length; i++) {
                    paramInfo.append(String.format("[%d]=%s ", i,
                            params[i] != null ? params[i].toString() : "null"));
                }
                log.error(paramInfo.toString());
            }
        }
    }
}
