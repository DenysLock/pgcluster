package com.pgcluster.api.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AuditLogFilterRequest {

    private UUID userId;

    private UUID clusterId;

    private String action;

    private String resourceType;

    private Instant startDate;

    private Instant endDate;
}
