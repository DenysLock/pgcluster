package com.pgcluster.api.service;

import com.pgcluster.api.client.HetznerClient;
import com.pgcluster.api.event.BackupCreatedEvent;
import com.pgcluster.api.model.dto.PitrRestoreRequest;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.RestoreJob;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.BackupRepository;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.RestoreJobRepository;
import com.pgcluster.api.repository.VpsNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BackupService")
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock private BackupRepository backupRepository;
    @Mock private RestoreJobRepository restoreJobRepository;
    @Mock private ClusterRepository clusterRepository;
    @Mock private VpsNodeRepository vpsNodeRepository;
    @Mock private S3StorageService s3StorageService;
    @Mock private PgBackRestService pgBackRestService;
    @Mock private ProvisioningService provisioningService;
    @Mock private PatroniService patroniService;
    @Mock private HetznerClient hetznerClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private BackupService backupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupService, "backupEnabled", true);
        ReflectionTestUtils.setField(backupService, "retentionDaily", 7);
        ReflectionTestUtils.setField(backupService, "retentionWeekly", 4);
        ReflectionTestUtils.setField(backupService, "retentionMonthly", 12);
    }

    @Nested
    @DisplayName("createBackup")
    class CreateBackup {

        @Test
        @DisplayName("should create pending backup and publish event")
        void shouldCreateBackup() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_IN_PROGRESS))
                    .thenReturn(List.of());
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_PENDING))
                    .thenReturn(List.of());
            when(backupRepository.save(any(Backup.class))).thenAnswer(inv -> {
                Backup b = inv.getArgument(0);
                b.setId(UUID.randomUUID());
                return b;
            });

            Backup result = backupService.createBackup(cluster.getId(), user);

            assertThat(result.getStatus()).isEqualTo(Backup.STATUS_PENDING);
            assertThat(result.getType()).isEqualTo(Backup.TYPE_MANUAL);
            assertThat(result.getRequestedBackupType()).isEqualTo(Backup.BACKUP_TYPE_INCR);

            verify(eventPublisher).publishEvent(any(BackupCreatedEvent.class));
            verify(auditLogService).logAsync(eq(AuditLog.BACKUP_INITIATED), eq(user), eq("backup"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when S3 not configured")
        void shouldThrowWhenS3NotConfigured() {
            when(s3StorageService.isConfigured()).thenReturn(false);

            assertThatThrownBy(() -> backupService.createBackup(UUID.randomUUID(), createTestUser()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when cluster not running")
        void shouldThrowWhenClusterNotRunning() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_PENDING);

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));

            assertThatThrownBy(() -> backupService.createBackup(cluster.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster must be running");
        }

        @Test
        @DisplayName("should throw when backup already in progress")
        void shouldThrowWhenBackupInProgress() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_IN_PROGRESS))
                    .thenReturn(List.of(new Backup()));

            assertThatThrownBy(() -> backupService.createBackup(cluster.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
        }

        @Test
        @DisplayName("should throw when backup is pending")
        void shouldThrowWhenBackupPending() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_IN_PROGRESS))
                    .thenReturn(List.of());
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_PENDING))
                    .thenReturn(List.of(new Backup()));

            assertThatThrownBy(() -> backupService.createBackup(cluster.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
        }

        @Test
        @DisplayName("should default to incremental backup type when null")
        void shouldDefaultToIncremental() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
            when(backupRepository.save(any(Backup.class))).thenAnswer(inv -> {
                Backup b = inv.getArgument(0);
                b.setId(UUID.randomUUID());
                return b;
            });

            Backup result = backupService.createBackup(cluster.getId(), user, null);

            assertThat(result.getRequestedBackupType()).isEqualTo(Backup.BACKUP_TYPE_INCR);
        }

        @Test
        @DisplayName("should accept explicit full backup type")
        void shouldAcceptFullType() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
            when(backupRepository.save(any(Backup.class))).thenAnswer(inv -> {
                Backup b = inv.getArgument(0);
                b.setId(UUID.randomUUID());
                return b;
            });

            Backup result = backupService.createBackup(cluster.getId(), user, "full");

            assertThat(result.getRequestedBackupType()).isEqualTo(Backup.BACKUP_TYPE_FULL);
        }

        @Test
        @DisplayName("should normalize 'differential' to 'diff'")
        void shouldNormalizeDifferentialType() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
            when(backupRepository.save(any(Backup.class))).thenAnswer(inv -> {
                Backup b = inv.getArgument(0);
                b.setId(UUID.randomUUID());
                return b;
            });

            Backup result = backupService.createBackup(cluster.getId(), user, "differential");

            assertThat(result.getRequestedBackupType()).isEqualTo(Backup.BACKUP_TYPE_DIFF);
        }

        @Test
        @DisplayName("should normalize 'incremental' to 'incr'")
        void shouldNormalizeIncrementalType() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
            when(backupRepository.save(any(Backup.class))).thenAnswer(inv -> {
                Backup b = inv.getArgument(0);
                b.setId(UUID.randomUUID());
                return b;
            });

            Backup result = backupService.createBackup(cluster.getId(), user, "incremental");

            assertThat(result.getRequestedBackupType()).isEqualTo(Backup.BACKUP_TYPE_INCR);
        }

        @Test
        @DisplayName("should throw for invalid backup type")
        void shouldThrowForInvalidType() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

            assertThatThrownBy(() -> backupService.createBackup(cluster.getId(), user, "snapshot"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid backup type");
        }
    }

    @Nested
    @DisplayName("deleteBackup")
    class DeleteBackup {

        @Test
        @DisplayName("should delete backup and mark as DELETED")
        void shouldDeleteBackup() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            Backup otherFullBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup, otherFullBackup));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);

            backupService.deleteBackup(cluster.getId(), backup.getId(), user, false);

            verify(pgBackRestService).expireSpecificBackup(eq(cluster), eq(leader), eq(backup.getPgbackrestLabel()));
            assertThat(backup.getStatus()).isEqualTo(Backup.STATUS_DELETED);
            verify(auditLogService).logAsync(eq(AuditLog.BACKUP_DELETED), eq(user), eq("backup"), eq(backup.getId()), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when backup already deleted")
        void shouldThrowWhenAlreadyDeleted() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            backup.setStatus(Backup.STATUS_DELETED);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));

            assertThatThrownBy(() -> backupService.deleteBackup(cluster.getId(), backup.getId(), user, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already deleted");
        }

        @Test
        @DisplayName("should throw when cluster not running")
        void shouldThrowWhenClusterNotRunning() {
            User user = createTestUser();
            Cluster cluster = createCluster(Cluster.STATUS_PENDING);
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));

            assertThatThrownBy(() -> backupService.deleteBackup(cluster.getId(), backup.getId(), user, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster must be running");
        }

        @Test
        @DisplayName("should throw when deleting the only full backup")
        void shouldThrowWhenDeletingOnlyFullBackup() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup)); // only one full backup

            assertThatThrownBy(() -> backupService.deleteBackup(cluster.getId(), backup.getId(), user, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete the only full backup");
        }

        @Test
        @DisplayName("should throw when dependents exist and not confirmed")
        void shouldThrowWhenUnconfirmedDependents() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Instant baseTime = Instant.parse("2026-01-01T12:00:00Z");

            Backup fullBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            fullBackup.setCreatedAt(baseTime);

            Backup otherFullBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            otherFullBackup.setCreatedAt(baseTime.minus(1, java.time.temporal.ChronoUnit.DAYS));

            // Dependent incr backup created AFTER the full backup (makes it a dependent)
            Backup dependentBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_INCR);
            dependentBackup.setCreatedAt(baseTime.plus(1, java.time.temporal.ChronoUnit.HOURS));

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(fullBackup.getId(), cluster)).thenReturn(Optional.of(fullBackup));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(dependentBackup, fullBackup, otherFullBackup));

            assertThatThrownBy(() -> backupService.deleteBackup(cluster.getId(), fullBackup.getId(), user, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dependent backup(s)");
        }
    }

    @Nested
    @DisplayName("listBackups")
    class ListBackups {

        @Test
        @DisplayName("should exclude deleted backups by default")
        void shouldExcludeDeletedByDefault() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup active = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            Backup deleted = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            deleted.setStatus(Backup.STATUS_DELETED);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterOrderByCreatedAtDesc(cluster))
                    .thenReturn(new java.util.ArrayList<>(List.of(active, deleted)));

            List<Backup> result = backupService.listBackups(cluster.getId(), user);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isNotEqualTo(Backup.STATUS_DELETED);
        }

        @Test
        @DisplayName("should include deleted backups when requested")
        void shouldIncludeDeletedWhenRequested() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup active = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            Backup deleted = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            deleted.setStatus(Backup.STATUS_DELETED);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterOrderByCreatedAtDesc(cluster))
                    .thenReturn(List.of(active, deleted));

            List<Backup> result = backupService.listBackups(cluster.getId(), user, true);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should throw when cluster not found")
        void shouldThrowWhenClusterNotFound() {
            User user = createTestUser();
            UUID clusterId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(clusterId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> backupService.listBackups(clusterId, user))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getBackup")
    class GetBackup {

        @Test
        @DisplayName("should return backup when found")
        void shouldReturnBackup() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));

            Backup result = backupService.getBackup(cluster.getId(), backup.getId(), user);

            assertThat(result.getId()).isEqualTo(backup.getId());
        }

        @Test
        @DisplayName("should throw when cluster not found")
        void shouldThrowWhenClusterNotFound() {
            User user = createTestUser();
            UUID clusterId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(clusterId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> backupService.getBackup(clusterId, UUID.randomUUID(), user))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw when backup not found")
        void shouldThrowWhenBackupNotFound() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            UUID backupId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backupId, cluster)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> backupService.getBackup(cluster.getId(), backupId, user))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getBackupDeletionInfo")
    class GetBackupDeletionInfo {

        @Test
        @DisplayName("should return deletion info for backup without dependents")
        void shouldReturnInfoNoDependents() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup));

            var info = backupService.getBackupDeletionInfo(cluster.getId(), backup.getId(), user);

            assertThat(info.getTotalCount()).isEqualTo(1);
            assertThat(info.isRequiresConfirmation()).isFalse();
        }

        @Test
        @DisplayName("should throw when backup already deleted")
        void shouldThrowWhenDeleted() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            backup.setStatus(Backup.STATUS_DELETED);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByIdAndCluster(backup.getId(), cluster)).thenReturn(Optional.of(backup));

            assertThatThrownBy(() -> backupService.getBackupDeletionInfo(cluster.getId(), backup.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already deleted");
        }
    }

    @Nested
    @DisplayName("getBackupMetrics")
    class GetBackupMetrics {

        @Test
        @DisplayName("should return metrics for cluster backups")
        void shouldReturnMetrics() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            backup.setSizeBytes(1024L * 1024L);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup));

            var metrics = backupService.getBackupMetrics(cluster.getId(), user);

            assertThat(metrics.get("totalSizeBytes")).isEqualTo(1024L * 1024L);
            assertThat(metrics.get("backupCount")).isEqualTo(1);
            assertThat(metrics.get("storageTrend")).isNotNull();
        }

        @Test
        @DisplayName("should handle empty backup list")
        void shouldHandleEmpty() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of());

            var metrics = backupService.getBackupMetrics(cluster.getId(), user);

            assertThat(metrics.get("totalSizeBytes")).isEqualTo(0L);
            assertThat(metrics.get("backupCount")).isEqualTo(0);
            assertThat(metrics.get("oldestBackup")).isNull();
            assertThat(metrics.get("newestBackup")).isNull();
        }
    }

    @Nested
    @DisplayName("getPitrWindow")
    class GetPitrWindow {

        @Test
        @DisplayName("should return available PITR window")
        void shouldReturnAvailableWindow() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            Backup older = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            older.setEarliestRecoveryTime(Instant.parse("2026-01-01T10:00:00Z"));
            older.setLatestRecoveryTime(Instant.parse("2026-01-01T11:00:00Z"));

            Backup newer = createCompletedBackup(cluster, Backup.BACKUP_TYPE_INCR);
            newer.setEarliestRecoveryTime(Instant.parse("2026-01-01T11:00:00Z"));
            newer.setLatestRecoveryTime(Instant.parse("2026-01-01T12:00:00Z"));

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(newer, older));

            var window = backupService.getPitrWindow(cluster.getId(), user);

            assertThat(window.isAvailable()).isTrue();
            assertThat(window.getEarliestPitrTime()).isEqualTo(older.getEarliestRecoveryTime());
            assertThat(window.getLatestPitrTime()).isEqualTo(newer.getLatestRecoveryTime());
        }

        @Test
        @DisplayName("should return unavailable when recovery timestamps are missing")
        void shouldReturnUnavailableWindow() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            backup.setEarliestRecoveryTime(null);
            backup.setLatestRecoveryTime(null);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup));

            var window = backupService.getPitrWindow(cluster.getId(), user);

            assertThat(window.isAvailable()).isFalse();
            assertThat(window.getEarliestPitrTime()).isNull();
            assertThat(window.getLatestPitrTime()).isNull();
        }
    }

    @Nested
    @DisplayName("restoreFromPitr")
    class RestoreFromPitr {

        @Test
        @DisplayName("should create PITR restore job using closest preceding backup")
        void shouldCreatePitrRestoreJob() {
            User user = createTestUser();
            Cluster sourceCluster = createRunningCluster();

            Backup older = createCompletedBackup(sourceCluster, Backup.BACKUP_TYPE_FULL);
            older.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
            older.setEarliestRecoveryTime(Instant.parse("2026-01-01T09:00:00Z"));
            older.setLatestRecoveryTime(Instant.parse("2026-01-01T10:30:00Z"));

            Backup newer = createCompletedBackup(sourceCluster, Backup.BACKUP_TYPE_INCR);
            newer.setCreatedAt(Instant.parse("2026-01-01T11:00:00Z"));
            newer.setEarliestRecoveryTime(Instant.parse("2026-01-01T10:30:00Z"));
            newer.setLatestRecoveryTime(Instant.parse("2026-01-01T12:00:00Z"));

            Instant targetTime = Instant.parse("2026-01-01T11:30:00Z");
            PitrRestoreRequest request = PitrRestoreRequest.builder()
                    .targetTime(targetTime)
                    .createNewCluster(true)
                    .newClusterName("restored-cluster")
                    .nodeRegions(List.of("fsn1"))
                    .nodeSize("cx23")
                    .postgresVersion("16")
                    .build();

            when(clusterRepository.findByIdAndUser(sourceCluster.getId(), user)).thenReturn(Optional.of(sourceCluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(sourceCluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(newer, older));
            when(restoreJobRepository.findPendingJobsForCluster(sourceCluster.getId())).thenReturn(List.of());
            when(hetznerClient.getAvailableLocationsForServerType("cx23")).thenReturn(Set.of("fsn1"));

            when(clusterRepository.save(any(Cluster.class))).thenAnswer(invocation -> {
                Cluster saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(UUID.randomUUID());
                }
                return saved;
            });

            when(restoreJobRepository.save(any(RestoreJob.class))).thenAnswer(invocation -> {
                RestoreJob job = invocation.getArgument(0);
                if (job.getId() == null) {
                    job.setId(UUID.randomUUID());
                }
                return job;
            });

            RestoreJob job = backupService.restoreFromPitr(sourceCluster.getId(), request, user);

            assertThat(job.getRestoreType()).isEqualTo(RestoreJob.TYPE_PITR);
            assertThat(job.getTargetTime()).isEqualTo(targetTime);
            assertThat(job.getBackup().getId()).isEqualTo(newer.getId());
            assertThat(job.getTargetCluster()).isNotNull();

            verify(auditLogService).logAsync(eq(AuditLog.BACKUP_RESTORE_INITIATED), eq(user), eq("backup"),
                    eq(newer.getId()), any(), any(), any());
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("should throw when target time is outside global PITR window")
        void shouldThrowWhenTargetOutsideWindow() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            backup.setEarliestRecoveryTime(Instant.parse("2026-01-01T09:00:00Z"));
            backup.setLatestRecoveryTime(Instant.parse("2026-01-01T10:00:00Z"));

            PitrRestoreRequest request = PitrRestoreRequest.builder()
                    .targetTime(Instant.parse("2026-01-01T10:30:00Z"))
                    .createNewCluster(true)
                    .newClusterName("restore-outside-window")
                    .nodeRegions(List.of("fsn1"))
                    .nodeSize("cx23")
                    .build();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup));

            assertThatThrownBy(() -> backupService.restoreFromPitr(cluster.getId(), request, user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("after the latest recovery time");
        }
    }

    @Nested
    @DisplayName("getRestoreJob")
    class GetRestoreJob {

        @Test
        @DisplayName("should return restore job when found")
        void shouldReturnJob() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            UUID jobId = UUID.randomUUID();
            RestoreJob job = RestoreJob.builder().id(jobId).build();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(restoreJobRepository.findByIdAndSourceCluster(jobId, cluster)).thenReturn(Optional.of(job));

            RestoreJob result = backupService.getRestoreJob(cluster.getId(), jobId, user);
            assertThat(result.getId()).isEqualTo(jobId);
        }

        @Test
        @DisplayName("should throw when job not found")
        void shouldThrowWhenNotFound() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            UUID jobId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(restoreJobRepository.findByIdAndSourceCluster(jobId, cluster)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> backupService.getRestoreJob(cluster.getId(), jobId, user))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("listRestoreJobs")
    class ListRestoreJobs {

        @Test
        @DisplayName("should return restore jobs for cluster")
        void shouldReturnJobs() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            RestoreJob job = RestoreJob.builder().id(UUID.randomUUID()).build();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(restoreJobRepository.findBySourceClusterOrderByCreatedAtDesc(cluster)).thenReturn(List.of(job));

            List<RestoreJob> result = backupService.listRestoreJobs(cluster.getId(), user);
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("scheduledBackups")
    class ScheduledBackups {

        @Test
        @DisplayName("scheduledDailyBackup should skip when backup disabled")
        void shouldSkipDailyWhenDisabled() {
            ReflectionTestUtils.setField(backupService, "backupEnabled", false);
            backupService.scheduledDailyBackup();
            verify(clusterRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("scheduledDailyBackup should skip when S3 not configured")
        void shouldSkipDailyWhenS3NotConfigured() {
            when(s3StorageService.isConfigured()).thenReturn(false);
            backupService.scheduledDailyBackup();
            verify(clusterRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("scheduledWeeklyBackup should skip when backup disabled")
        void shouldSkipWeeklyWhenDisabled() {
            ReflectionTestUtils.setField(backupService, "backupEnabled", false);
            backupService.scheduledWeeklyBackup();
            verify(clusterRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("scheduledWeeklyBackup should skip when S3 not configured")
        void shouldSkipWeeklyWhenS3NotConfigured() {
            when(s3StorageService.isConfigured()).thenReturn(false);
            backupService.scheduledWeeklyBackup();
            verify(clusterRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("scheduledMonthlyBackup should skip when backup disabled")
        void shouldSkipMonthlyWhenDisabled() {
            ReflectionTestUtils.setField(backupService, "backupEnabled", false);
            backupService.scheduledMonthlyBackup();
            verify(clusterRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("scheduledMonthlyBackup should skip when S3 not configured")
        void shouldSkipMonthlyWhenS3NotConfigured() {
            when(s3StorageService.isConfigured()).thenReturn(false);
            backupService.scheduledMonthlyBackup();
            verify(clusterRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("cleanupExpiredBackups should skip when backup disabled")
        void shouldSkipCleanupWhenDisabled() {
            ReflectionTestUtils.setField(backupService, "backupEnabled", false);
            backupService.cleanupExpiredBackups();
            verify(backupRepository, never()).findExpiredBackups(any());
        }

        @Test
        @DisplayName("cleanupExpiredBackups should skip when S3 not configured")
        void shouldSkipCleanupWhenS3NotConfigured() {
            when(s3StorageService.isConfigured()).thenReturn(false);
            backupService.cleanupExpiredBackups();
            verify(backupRepository, never()).findExpiredBackups(any());
        }

        @Test
        @DisplayName("cleanupExpiredBackups should delete S3 files and mark as expired")
        void shouldCleanupExpiredBackups() {
            Backup expired = Backup.builder()
                    .id(UUID.randomUUID())
                    .s3BasePath("pgbackrest/test-cluster")
                    .status(Backup.STATUS_COMPLETED)
                    .build();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(backupRepository.findExpiredBackups(any(Instant.class))).thenReturn(List.of(expired));

            backupService.cleanupExpiredBackups();

            verify(s3StorageService).deleteDirectory("pgbackrest/test-cluster");
            assertThat(expired.getStatus()).isEqualTo(Backup.STATUS_EXPIRED);
            verify(backupRepository).save(expired);
        }

        @Test
        @DisplayName("cleanupExpiredBackups should continue on S3 deletion failure")
        void shouldContinueCleanupOnS3Failure() {
            Backup expired = Backup.builder()
                    .id(UUID.randomUUID())
                    .s3BasePath("pgbackrest/test-cluster")
                    .status(Backup.STATUS_COMPLETED)
                    .build();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(backupRepository.findExpiredBackups(any(Instant.class))).thenReturn(List.of(expired));
            doThrow(new RuntimeException("S3 error")).when(s3StorageService).deleteDirectory(any());

            backupService.cleanupExpiredBackups();

            // Should not mark as expired if S3 delete failed
            assertThat(expired.getStatus()).isEqualTo(Backup.STATUS_COMPLETED);
        }
    }

    @Nested
    @DisplayName("deleteBackupAsAdmin")
    class DeleteBackupAsAdmin {

        @Test
        @DisplayName("should delete backup and mark as DELETED")
        void shouldDeleteAsAdmin() {
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            Backup otherFullBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup, otherFullBackup));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);

            backupService.deleteBackupAsAdmin(cluster, backup, false);

            verify(pgBackRestService).expireSpecificBackup(eq(cluster), eq(leader), eq(backup.getPgbackrestLabel()));
            assertThat(backup.getStatus()).isEqualTo(Backup.STATUS_DELETED);
        }

        @Test
        @DisplayName("should throw when backup already deleted")
        void shouldThrowWhenAlreadyDeleted() {
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            backup.setStatus(Backup.STATUS_DELETED);

            assertThatThrownBy(() -> backupService.deleteBackupAsAdmin(cluster, backup, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already deleted");
        }

        @Test
        @DisplayName("should throw when cluster not running")
        void shouldThrowWhenClusterNotRunning() {
            Cluster cluster = createCluster(Cluster.STATUS_PENDING);
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            assertThatThrownBy(() -> backupService.deleteBackupAsAdmin(cluster, backup, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster must be running");
        }

        @Test
        @DisplayName("should throw when deleting the only full backup")
        void shouldThrowWhenDeletingOnlyFullBackup() {
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup));

            assertThatThrownBy(() -> backupService.deleteBackupAsAdmin(cluster, backup, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete the only full backup");
        }

        @Test
        @DisplayName("should throw when dependents exist and not confirmed")
        void shouldThrowWhenDependentsNotConfirmed() {
            Cluster cluster = createRunningCluster();
            Instant baseTime = Instant.parse("2026-01-01T12:00:00Z");

            Backup fullBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            fullBackup.setCreatedAt(baseTime);

            Backup otherFull = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            otherFull.setCreatedAt(baseTime.minus(1, java.time.temporal.ChronoUnit.DAYS));

            Backup dependent = createCompletedBackup(cluster, Backup.BACKUP_TYPE_INCR);
            dependent.setCreatedAt(baseTime.plus(1, java.time.temporal.ChronoUnit.HOURS));

            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(dependent, fullBackup, otherFull));

            assertThatThrownBy(() -> backupService.deleteBackupAsAdmin(cluster, fullBackup, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dependent backup(s)");
        }

        @Test
        @DisplayName("should delete with dependents when confirmed")
        void shouldDeleteWithDependentsWhenConfirmed() {
            Cluster cluster = createRunningCluster();
            Instant baseTime = Instant.parse("2026-01-01T12:00:00Z");

            Backup fullBackup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            fullBackup.setCreatedAt(baseTime);

            Backup otherFull = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);
            otherFull.setCreatedAt(baseTime.minus(1, java.time.temporal.ChronoUnit.DAYS));

            Backup dependent = createCompletedBackup(cluster, Backup.BACKUP_TYPE_INCR);
            dependent.setCreatedAt(baseTime.plus(1, java.time.temporal.ChronoUnit.HOURS));

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(dependent, fullBackup, otherFull));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);

            backupService.deleteBackupAsAdmin(cluster, fullBackup, true);

            assertThat(fullBackup.getStatus()).isEqualTo(Backup.STATUS_DELETED);
            assertThat(dependent.getStatus()).isEqualTo(Backup.STATUS_DELETED);
        }

        @Test
        @DisplayName("should skip pgBackRest deletion when label is null")
        void shouldSkipPgBackRestWhenLabelNull() {
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_INCR);
            backup.setPgbackrestLabel(null);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            // findDependentBackups returns empty since pgbackrestLabel is null
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);

            backupService.deleteBackupAsAdmin(cluster, backup, false);

            verify(pgBackRestService, never()).expireSpecificBackup(any(), any(), any());
            assertThat(backup.getStatus()).isEqualTo(Backup.STATUS_DELETED);
        }

        @Test
        @DisplayName("should throw when no leader node found")
        void shouldThrowWhenNoLeader() {
            Cluster cluster = createRunningCluster();
            Backup backup = createCompletedBackup(cluster, Backup.BACKUP_TYPE_INCR);
            Backup otherFull = createCompletedBackup(cluster, Backup.BACKUP_TYPE_FULL);

            when(backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED))
                    .thenReturn(List.of(backup, otherFull));
            when(patroniService.findLeaderNode(cluster)).thenReturn(null);

            assertThatThrownBy(() -> backupService.deleteBackupAsAdmin(cluster, backup, false))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("No leader node found");
        }
    }

    @Nested
    @DisplayName("markBackupFailed (via executeBackupAsync)")
    class MarkBackupFailed {

        @Test
        @DisplayName("should mark backup as failed when execution throws")
        void shouldMarkBackupFailed() {
            UUID backupId = UUID.randomUUID();
            Backup backup = Backup.builder().id(backupId).status(Backup.STATUS_PENDING).build();

            when(backupRepository.findById(backupId)).thenReturn(Optional.of(backup));

            // executeBackupAsync calls executeBackup which will fail since cluster is null
            backupService.executeBackupAsync(backupId);

            // The error handling in executeBackupAsync calls markBackupFailed
            verify(backupRepository, atLeastOnce()).save(argThat(b ->
                    Backup.STATUS_FAILED.equals(b.getStatus())));
        }
    }

    // ==================== Helpers ====================

    private User createTestUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hash")
                .role("user")
                .active(true)
                .build();
    }

    private Cluster createRunningCluster() {
        return createCluster(Cluster.STATUS_RUNNING);
    }

    private Cluster createCluster(String status) {
        return Cluster.builder()
                .id(UUID.randomUUID())
                .name("test-cluster")
                .slug("test-cluster-abc123")
                .status(status)
                .nodeCount(1)
                .nodeSize("cx23")
                .postgresVersion("16")
                .build();
    }

    private Backup createCompletedBackup(Cluster cluster, String backupType) {
        return Backup.builder()
                .id(UUID.randomUUID())
                .cluster(cluster)
                .status(Backup.STATUS_COMPLETED)
                .type(Backup.TYPE_MANUAL)
                .backupType(backupType)
                .requestedBackupType(backupType)
                .pgbackrestLabel("20260203-120000F")
                .sizeBytes(1024L * 1024L)
                .createdAt(Instant.now())
                .build();
    }
}
