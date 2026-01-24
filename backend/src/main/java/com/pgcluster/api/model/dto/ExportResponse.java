package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.util.FormatUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ExportResponse {

    private UUID id;
    private UUID clusterId;
    private String status;
    private String format;
    private Long sizeBytes;
    private String formattedSize;
    private String downloadUrl;
    private Instant downloadExpiresAt;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static ExportResponse fromEntity(Export export) {
        return ExportResponse.builder()
                .id(export.getId())
                .clusterId(export.getCluster().getId())
                .status(export.getStatus())
                .format(export.getFormat())
                .sizeBytes(export.getSizeBytes())
                .formattedSize(FormatUtils.formatBytes(export.getSizeBytes()))
                .downloadUrl(export.getDownloadUrl())
                .downloadExpiresAt(export.getDownloadExpiresAt())
                .errorMessage(export.getErrorMessage())
                .startedAt(export.getStartedAt())
                .completedAt(export.getCompletedAt())
                .createdAt(export.getCreatedAt())
                .updatedAt(export.getUpdatedAt())
                .build();
    }
}
