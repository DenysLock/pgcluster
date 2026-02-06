package com.pgcluster.api.model.dto;

import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BackupDeletionInfo")
class BackupDeletionInfoTest {

    @Test
    @DisplayName("should calculate deletion info with dependent backups")
    void shouldCalculateWithDependents() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).slug("test").build();

        Backup primary = Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_FULL)
                .sizeBytes(1024L * 1024L)
                .createdAt(Instant.now())
                .build();

        Backup dependent = Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_INCR)
                .sizeBytes(512L * 1024L)
                .createdAt(Instant.now())
                .build();

        BackupDeletionInfo info = BackupDeletionInfo.from(primary, List.of(dependent));

        assertThat(info.getTotalCount()).isEqualTo(2);
        assertThat(info.getTotalSizeBytes()).isEqualTo(1024L * 1024L + 512L * 1024L);
        assertThat(info.isRequiresConfirmation()).isTrue();
        assertThat(info.getWarningMessage()).contains("1 dependent backup(s)");
        assertThat(info.getFormattedTotalSize()).contains("MB");
    }

    @Test
    @DisplayName("should handle null size bytes")
    void shouldHandleNullSizeBytes() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).slug("test").build();

        Backup primary = Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_FULL)
                .sizeBytes(null)
                .createdAt(Instant.now())
                .build();

        BackupDeletionInfo info = BackupDeletionInfo.from(primary, List.of());

        assertThat(info.getTotalSizeBytes()).isEqualTo(0);
        assertThat(info.isRequiresConfirmation()).isFalse();
        assertThat(info.getWarningMessage()).isNull();
    }

    @Test
    @DisplayName("should format sizes in different units")
    void shouldFormatDifferentSizes() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).slug("test").build();

        // Test KB range
        Backup small = Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_FULL)
                .sizeBytes(2048L)
                .createdAt(Instant.now())
                .build();

        BackupDeletionInfo info = BackupDeletionInfo.from(small, List.of());
        assertThat(info.getFormattedTotalSize()).contains("KB");

        // Test GB range
        Backup large = Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_FULL)
                .sizeBytes(2L * 1024 * 1024 * 1024)
                .createdAt(Instant.now())
                .build();

        BackupDeletionInfo infoLarge = BackupDeletionInfo.from(large, List.of());
        assertThat(infoLarge.getFormattedTotalSize()).contains("GB");

        // Test B range
        Backup tiny = Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .backupType(Backup.BACKUP_TYPE_FULL)
                .sizeBytes(500L)
                .createdAt(Instant.now())
                .build();

        BackupDeletionInfo infoTiny = BackupDeletionInfo.from(tiny, List.of());
        assertThat(infoTiny.getFormattedTotalSize()).contains("B");
    }
}
