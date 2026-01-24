package com.pgcluster.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP request/response logging interceptor.
 * Logs request method, URI, response status, and duration for all API requests.
 */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        if (log.isDebugEnabled()) {
            log.debug("Request: {} {} from {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    getClientIp(request));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Log at different levels based on status code
        if (status >= 500) {
            log.error("Response: {} {} -> {} ({}ms){}",
                    method, uri, status, duration,
                    ex != null ? " - " + ex.getMessage() : "");
        } else if (status >= 400) {
            log.warn("Response: {} {} -> {} ({}ms)",
                    method, uri, status, duration);
        } else if (log.isInfoEnabled()) {
            // Only log successful requests at INFO level for significant operations
            if (isSignificantOperation(method)) {
                log.info("Response: {} {} -> {} ({}ms)",
                        method, uri, status, duration);
            } else if (log.isDebugEnabled()) {
                log.debug("Response: {} {} -> {} ({}ms)",
                        method, uri, status, duration);
            }
        }
    }

    /**
     * Determine if this is a significant operation worth logging at INFO level.
     */
    private boolean isSignificantOperation(String method) {
        return "POST".equals(method) || "PUT".equals(method) ||
               "DELETE".equals(method) || "PATCH".equals(method);
    }

    /**
     * Get the client IP address, handling proxied requests.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs; the first one is the client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
