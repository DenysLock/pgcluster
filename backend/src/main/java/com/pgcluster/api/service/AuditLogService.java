package com.pgcluster.api.service;

import com.pgcluster.api.model.dto.AuditLogFilterRequest;
import com.pgcluster.api.model.dto.AuditLogListResponse;
import com.pgcluster.api.model.dto.AuditLogResponse;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event asynchronously to avoid blocking the main request.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(String action, User user, String resourceType, UUID resourceId, Map<String, Object> details) {
        doLog(action, user, resourceType, resourceId, details);
    }

    /**
     * Log an audit event asynchronously with pre-captured IP and user-agent.
     * Use this when calling from async context where HTTP request context is lost.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(String action, User user, String resourceType, UUID resourceId, Map<String, Object> details, String ipAddress, String userAgent) {
        doLogWithIp(action, user, resourceType, resourceId, details, ipAddress, userAgent);
    }

    /**
     * Get the client IP from the current HTTP request context.
     * Call this BEFORE async operations to capture IP while still in HTTP context.
     */
    public String getCurrentRequestIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return getClientIp(attrs.getRequest());
        }
        return null;
    }

    /**
     * Get the user-agent from the current HTTP request context.
     * Call this BEFORE async operations to capture user-agent while still in HTTP context.
     */
    public String getCurrentRequestUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest().getHeader("User-Agent");
        }
        return null;
    }

    /**
     * Log an audit event synchronously.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, User user, String resourceType, UUID resourceId, Map<String, Object> details) {
        doLog(action, user, resourceType, resourceId, details);
    }

    private void doLog(String action, User user, String resourceType, UUID resourceId, Map<String, Object> details) {
        try {
            String ipAddress = null;
            String userAgent = null;

            // Try to extract request info from current context
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = getClientIp(request);
                userAgent = request.getHeader("User-Agent");
            }

            saveAuditLog(action, user, resourceType, resourceId, details, ipAddress, userAgent);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    private void doLogWithIp(String action, User user, String resourceType, UUID resourceId, Map<String, Object> details, String ipAddress, String userAgent) {
        try {
            saveAuditLog(action, user, resourceType, resourceId, details, ipAddress, userAgent);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    private void saveAuditLog(String action, User user, String resourceType, UUID resourceId,
                               Map<String, Object> details, String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .timestamp(Instant.now())
                .userId(user != null ? user.getId() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log saved: action={}, user={}, resource={}/{}",
                action, user != null ? user.getEmail() : "anonymous", resourceType, resourceId);
    }

    /**
     * Log authentication event (used when user may not be authenticated yet).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuth(String action, String email, boolean success, String failureReason) {
        try {
            String ipAddress = null;
            String userAgent = null;

            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = getClientIp(request);
                userAgent = request.getHeader("User-Agent");
            }

            Map<String, Object> details = success
                    ? Map.of("success", true)
                    : Map.of("success", false, "reason", failureReason != null ? failureReason : "unknown");

            AuditLog auditLog = AuditLog.builder()
                    .timestamp(Instant.now())
                    .userEmail(email)
                    .action(action)
                    .resourceType("auth")
                    .details(details)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save auth audit log: {}", e.getMessage());
        }
    }

    /**
     * Get paginated audit logs with filters.
     */
    @Transactional(readOnly = true)
    public AuditLogListResponse getAuditLogs(AuditLogFilterRequest filter, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));

        Page<AuditLog> logs = auditLogRepository.findWithFilters(
                filter.getUserId(),
                filter.getClusterId() != null ? filter.getClusterId().toString() : null,
                filter.getAction(),
                filter.getResourceType(),
                filter.getStartDate(),
                filter.getEndDate(),
                pageRequest
        );

        List<AuditLogResponse> responses = logs.getContent().stream()
                .map(AuditLogResponse::fromEntity)
                .toList();

        return AuditLogListResponse.builder()
                .logs(responses)
                .page(page)
                .size(size)
                .totalElements(logs.getTotalElements())
                .totalPages(logs.getTotalPages())
                .build();
    }

    /**
     * Export audit logs as CSV (no pagination, includes user_agent).
     */
    @Transactional(readOnly = true)
    public String exportAuditLogsCsv(AuditLogFilterRequest filter) {
        List<AuditLog> logs = auditLogRepository.findWithFiltersForExport(
                filter.getUserId(),
                filter.getClusterId() != null ? filter.getClusterId().toString() : null,
                filter.getAction(),
                filter.getResourceType(),
                filter.getStartDate(),
                filter.getEndDate()
        );

        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,user_email,action,resource_type,resource_id,ip_address,user_agent,details\n");

        for (AuditLog entry : logs) {
            csv.append(escapeCsv(entry.getTimestamp() != null ? entry.getTimestamp().toString() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getUserEmail() != null ? entry.getUserEmail() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getAction() != null ? entry.getAction() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getResourceType() != null ? entry.getResourceType() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getResourceId() != null ? entry.getResourceId().toString() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getIpAddress() != null ? entry.getIpAddress() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getUserAgent() != null ? entry.getUserAgent() : ""));
            csv.append(',');
            csv.append(escapeCsv(entry.getDetails() != null ? entry.getDetails().toString() : ""));
            csv.append('\n');
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Get recent activity for a specific user.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getUserActivity(UUID userId) {
        return auditLogRepository.findTop50ByUserIdOrderByTimestampDesc(userId).stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
