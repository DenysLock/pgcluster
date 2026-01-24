package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.RestoreJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class RestoreJobResponse {

    private UUID id;
    private UUID sourceClusterId;
    private UUID targetClusterId;
    private UUID backupId;
    private String restoreType;
    private Instant targetTime;
    private String status;
    private String currentStep;
    private Integer progress;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public static RestoreJobResponse fromEntity(RestoreJob job) {
        return RestoreJobResponse.builder()
                .id(job.getId())
                .sourceClusterId(job.getSourceCluster().getId())
                .targetClusterId(job.getTargetCluster() != null ? job.getTargetCluster().getId() : null)
                .backupId(job.getBackup().getId())
                .restoreType(job.getRestoreType())
                .targetTime(job.getTargetTime())
                .status(job.getStatus())
                .currentStep(job.getCurrentStep())
                .progress(job.getProgress())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}
