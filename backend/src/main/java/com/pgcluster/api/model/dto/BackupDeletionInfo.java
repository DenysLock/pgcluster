package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Backup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about what will be deleted when deleting a backup.
 * Used to show a confirmation dialog to the user with cascade deletion details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupDeletionInfo {

    /**
     * The primary backup being deleted
     */
    private BackupResponse backup;

    /**
     * Dependent backups that will also be deleted (cascade)
     */
    private List<BackupResponse> dependentBackups;

    /**
     * Total number of backups that will be deleted
     */
    private int totalCount;

    /**
     * Total size in bytes that will be freed
     */
    private long totalSizeBytes;

    /**
     * Formatted total size (e.g., "17.48 MB")
     */
    private String formattedTotalSize;

    /**
     * Whether this requires confirmation (true if there are dependent backups)
     */
    private boolean requiresConfirmation;

    /**
     * Warning message for the user
     */
    private String warningMessage;

    public static BackupDeletionInfo from(Backup backup, List<Backup> dependentBackups) {
        List<BackupResponse> dependentResponses = dependentBackups.stream()
                .map(BackupResponse::fromEntity)
                .toList();

        long totalSize = backup.getSizeBytes() != null ? backup.getSizeBytes() : 0;
        totalSize += dependentBackups.stream()
                .mapToLong(b -> b.getSizeBytes() != null ? b.getSizeBytes() : 0)
                .sum();

        int totalCount = 1 + dependentBackups.size();
        boolean requiresConfirmation = !dependentBackups.isEmpty();

        String warning = null;
        if (requiresConfirmation) {
            warning = String.format(
                    "This %s backup has %d dependent backup(s) that will also be deleted. " +
                    "After deletion, the next backup will automatically be a full backup.",
                    backup.getBackupType(),
                    dependentBackups.size()
            );
        }

        return BackupDeletionInfo.builder()
                .backup(BackupResponse.fromEntity(backup))
                .dependentBackups(dependentResponses)
                .totalCount(totalCount)
                .totalSizeBytes(totalSize)
                .formattedTotalSize(formatSize(totalSize))
                .requiresConfirmation(requiresConfirmation)
                .warningMessage(warning)
                .build();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
