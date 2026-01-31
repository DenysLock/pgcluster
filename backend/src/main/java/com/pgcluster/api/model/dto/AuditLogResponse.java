package com.pgcluster.api.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgcluster.api.model.entity.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class AuditLogResponse {

    private UUID id;

    private Instant timestamp;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("user_email")
    private String userEmail;

    private String action;

    @JsonProperty("resource_type")
    private String resourceType;

    @JsonProperty("resource_id")
    private UUID resourceId;

    private Map<String, Object> details;

    @JsonProperty("ip_address")
    private String ipAddress;

    public static AuditLogResponse fromEntity(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .build();
    }
}
