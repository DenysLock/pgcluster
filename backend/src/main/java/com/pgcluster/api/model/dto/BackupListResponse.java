package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.util.FormatUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class BackupListResponse {

    private List<BackupResponse> backups;
    private int count;
    private long totalSizeBytes;
    private String formattedTotalSize;

    public static BackupListResponse fromEntities(List<Backup> backups) {
        List<BackupResponse> responses = backups.stream()
                .map(BackupResponse::fromEntity)
                .toList();

        long totalSize = backups.stream()
                .filter(b -> b.getSizeBytes() != null)
                .mapToLong(Backup::getSizeBytes)
                .sum();

        return BackupListResponse.builder()
                .backups(responses)
                .count(responses.size())
                .totalSizeBytes(totalSize)
                .formattedTotalSize(FormatUtils.formatBytes(totalSize))
                .build();
    }
}
