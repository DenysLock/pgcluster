package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.util.FormatUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class BackupResponse {

    private UUID id;
    private UUID clusterId;
    private String type;
    private String backupType;
    private String status;
    private String currentStep;
    private Integer progressPercent;
    private Long sizeBytes;
    private String formattedSize;
    private String s3BasePath;
    private String walStartLsn;
    private String walEndLsn;
    private Instant earliestRecoveryTime;
    private Instant latestRecoveryTime;
    private String retentionType;
    private Instant expiresAt;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String pgbackrestLabel;

    public static BackupResponse fromEntity(Backup backup) {
        return BackupResponse.builder()
                .id(backup.getId())
                .clusterId(backup.getCluster().getId())
                .type(backup.getType())
                .backupType(backup.getBackupType())
                .status(backup.getStatus())
                .currentStep(backup.getCurrentStep())
                .progressPercent(backup.getProgressPercent())
                .sizeBytes(backup.getSizeBytes())
                .formattedSize(FormatUtils.formatBytes(backup.getSizeBytes()))
                .s3BasePath(backup.getS3BasePath())
                .walStartLsn(backup.getWalStartLsn())
                .walEndLsn(backup.getWalEndLsn())
                .earliestRecoveryTime(backup.getEarliestRecoveryTime())
                .latestRecoveryTime(backup.getLatestRecoveryTime())
                .retentionType(backup.getRetentionType())
                .expiresAt(backup.getExpiresAt())
                .errorMessage(backup.getErrorMessage())
                .startedAt(backup.getStartedAt())
                .completedAt(backup.getCompletedAt())
                .createdAt(backup.getCreatedAt())
                .updatedAt(backup.getUpdatedAt())
                .pgbackrestLabel(backup.getPgbackrestLabel())
                .build();
    }
}
