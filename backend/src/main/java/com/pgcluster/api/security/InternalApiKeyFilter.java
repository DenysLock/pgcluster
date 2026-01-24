package com.pgcluster.api.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to protect internal endpoints
 * with a static API key. This prevents unauthorized access to infrastructure details.
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    @Value("${security.internal-api-key:}")
    private String internalApiKey;

    @PostConstruct
    public void init() {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException(
                "INTERNAL_API_KEY environment variable must be set. " +
                "Generate a secure random string for internal endpoint authentication."
            );
        }
        if (internalApiKey.length() < 32) {
            throw new IllegalStateException(
                "INTERNAL_API_KEY must be at least 32 characters for security. " +
                "Current length: " + internalApiKey.length()
            );
        }
        log.info("Internal API key filter initialized");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // Only apply to internal endpoints
        if (requestUri.startsWith("/internal/")) {
            String providedKey = extractApiKey(request);

            if (providedKey == null || providedKey.isBlank()) {
                log.warn("Missing API key for internal endpoint: {} from IP: {}",
                        requestUri, request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Missing API key. Use X-Internal-Api-Key header or Authorization: Bearer\"}");
                return;
            }

            if (!internalApiKey.equals(providedKey)) {
                log.warn("Invalid API key for internal endpoint: {} from IP: {}",
                        requestUri, request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid API key\"}");
                return;
            }

            log.debug("Internal API key validated for: {}", requestUri);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract API key from request headers.
     * Supports both X-Internal-Api-Key header and Authorization: Bearer format.
     * This allows Prometheus http_sd_configs to authenticate using standard Bearer auth.
     */
    private String extractApiKey(HttpServletRequest request) {
        // First, check X-Internal-Api-Key header
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }

        // Fall back to Authorization: Bearer header (used by Prometheus)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
