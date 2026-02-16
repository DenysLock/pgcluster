package com.pgcluster.api.service;

import com.pgcluster.api.event.ExportCreatedEvent;
import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.ExportRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;

@DisplayName("ExportService")
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private ExportRepository exportRepository;
    @Mock private ClusterRepository clusterRepository;
    @Mock private S3StorageService s3StorageService;
    @Mock private SshService sshService;
    @Mock private PatroniService patroniService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ExportService exportService;

    @Nested
    @DisplayName("createExport")
    class CreateExport {

        @Test
        @DisplayName("should create pending export and publish event")
        void shouldCreateExportAndPublishEvent() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Export.STATUS_IN_PROGRESS))
                    .thenReturn(List.of());
            when(exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Export.STATUS_PENDING))
                    .thenReturn(List.of());
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> {
                Export e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            Export result = exportService.createExport(cluster.getId(), user);

            assertThat(result.getStatus()).isEqualTo(Export.STATUS_PENDING);
            assertThat(result.getFormat()).isEqualTo(Export.FORMAT_PG_DUMP);

            verify(eventPublisher).publishEvent(any(ExportCreatedEvent.class));
            verify(auditLogService).logAsync(eq(AuditLog.EXPORT_INITIATED), eq(user), eq("export"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when S3 not configured")
        void shouldThrowWhenS3NotConfigured() {
            when(s3StorageService.isConfigured()).thenReturn(false);

            assertThatThrownBy(() -> exportService.createExport(UUID.randomUUID(), createTestUser()))
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

            assertThatThrownBy(() -> exportService.createExport(cluster.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster must be running");
        }

        @Test
        @DisplayName("should throw when export already in progress")
        void shouldThrowWhenExportInProgress() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Export.STATUS_IN_PROGRESS))
                    .thenReturn(List.of(new Export()));

            assertThatThrownBy(() -> exportService.createExport(cluster.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
        }

        @Test
        @DisplayName("should throw when export is pending")
        void shouldThrowWhenExportPending() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Export.STATUS_IN_PROGRESS))
                    .thenReturn(List.of());
            when(exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Export.STATUS_PENDING))
                    .thenReturn(List.of(new Export()));

            assertThatThrownBy(() -> exportService.createExport(cluster.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
        }

        @Test
        @DisplayName("should throw when cluster not found")
        void shouldThrowWhenClusterNotFound() {
            User user = createTestUser();
            UUID clusterId = UUID.randomUUID();

            when(s3StorageService.isConfigured()).thenReturn(true);
            when(clusterRepository.findByIdAndUser(clusterId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> exportService.createExport(clusterId, user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cluster not found");
        }
    }

    @Nested
    @DisplayName("deleteExport")
    class DeleteExport {

        @Test
        @DisplayName("should delete export from S3 and repository")
        void shouldDeleteFromS3AndDb() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path("exports/test/file.sql.gz");
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));
            when(s3StorageService.isConfigured()).thenReturn(true);

            exportService.deleteExport(cluster.getId(), export.getId(), user);

            verify(s3StorageService).deleteFile("exports/test/file.sql.gz");
            verify(exportRepository).delete(export);
            verify(auditLogService).logAsync(eq(AuditLog.EXPORT_DELETED), eq(user), eq("export"), eq(export.getId()), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when deleting in-progress export")
        void shouldThrowWhenDeletingInProgress() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_IN_PROGRESS);
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));

            assertThatThrownBy(() -> exportService.deleteExport(cluster.getId(), export.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete an export that is in progress");
        }

        @Test
        @DisplayName("should continue deletion when S3 delete fails")
        void shouldContinueWhenS3Fails() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path("exports/test/file.sql.gz");
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));
            when(s3StorageService.isConfigured()).thenReturn(true);
            doThrow(new RuntimeException("S3 error")).when(s3StorageService).deleteFile(any());

            exportService.deleteExport(cluster.getId(), export.getId(), user);

            // Should still delete from DB despite S3 failure
            verify(exportRepository).delete(export);
        }

        @Test
        @DisplayName("should skip S3 deletion when s3Path is null")
        void shouldSkipS3WhenPathNull() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path(null);
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));

            exportService.deleteExport(cluster.getId(), export.getId(), user);

            verify(s3StorageService, never()).deleteFile(any());
            verify(exportRepository).delete(export);
        }

        @Test
        @DisplayName("should skip S3 deletion when S3 not configured")
        void shouldSkipS3WhenNotConfigured() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path("exports/test/file.sql.gz");
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));
            when(s3StorageService.isConfigured()).thenReturn(false);

            exportService.deleteExport(cluster.getId(), export.getId(), user);

            verify(s3StorageService, never()).deleteFile(any());
            verify(exportRepository).delete(export);
        }
    }

    @Nested
    @DisplayName("refreshDownloadUrl")
    class RefreshDownloadUrl {

        @Test
        @DisplayName("should generate new presigned URL for completed export")
        void shouldRefreshUrl() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path("exports/test/file.sql.gz");
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));
            when(s3StorageService.generatePresignedUrl(eq("exports/test/file.sql.gz"), anyInt()))
                    .thenReturn("https://s3.example.com/presigned-url");
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> inv.getArgument(0));

            Export result = exportService.refreshDownloadUrl(cluster.getId(), export.getId(), user);

            assertThat(result.getDownloadUrl()).isEqualTo("https://s3.example.com/presigned-url");
            assertThat(result.getDownloadExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw when export not completed")
        void shouldThrowWhenNotCompleted() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_FAILED);
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));

            assertThatThrownBy(() -> exportService.refreshDownloadUrl(cluster.getId(), export.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only refresh download URL for completed exports");
        }

        @Test
        @DisplayName("should throw when export has no S3 path")
        void shouldThrowWhenNoS3Path() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path(null);
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));

            assertThatThrownBy(() -> exportService.refreshDownloadUrl(cluster.getId(), export.getId(), user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Export has no S3 path");
        }
    }

    @Nested
    @DisplayName("listExports")
    class ListExports {

        @Test
        @DisplayName("should return all exports for user's cluster")
        void shouldReturnExports() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByClusterOrderByCreatedAtDesc(cluster))
                    .thenReturn(List.of(createExport("completed"), createExport("failed"), createExport("pending")));

            List<Export> result = exportService.listExports(cluster.getId(), user);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should throw when cluster not found")
        void shouldThrowWhenClusterNotFound() {
            User user = createTestUser();
            UUID clusterId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(clusterId, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> exportService.listExports(clusterId, user))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("deleteExportAsAdmin")
    class DeleteExportAsAdmin {

        @Test
        @DisplayName("should delete export from S3 and DB as admin")
        void shouldDeleteAsAdmin() {
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setS3Path("exports/test/file.sql.gz");
            export.setCluster(cluster);

            when(s3StorageService.isConfigured()).thenReturn(true);

            exportService.deleteExportAsAdmin(cluster, export);

            verify(s3StorageService).deleteFile("exports/test/file.sql.gz");
            verify(exportRepository).delete(export);
        }

        @Test
        @DisplayName("should throw when export is in progress")
        void shouldThrowWhenInProgress() {
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_IN_PROGRESS);
            export.setCluster(cluster);

            assertThatThrownBy(() -> exportService.deleteExportAsAdmin(cluster, export))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete an export that is in progress");
        }
    }

    @Nested
    @DisplayName("getExport")
    class GetExport {

        @Test
        @DisplayName("should return export when found")
        void shouldReturnExport() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_COMPLETED);
            export.setCluster(cluster);

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(export.getId(), cluster)).thenReturn(Optional.of(export));

            Export result = exportService.getExport(cluster.getId(), export.getId(), user);
            assertThat(result.getId()).isEqualTo(export.getId());
        }

        @Test
        @DisplayName("should throw when export not found")
        void shouldThrowWhenNotFound() {
            User user = createTestUser();
            Cluster cluster = createRunningCluster();
            UUID exportId = UUID.randomUUID();

            when(clusterRepository.findByIdAndUser(cluster.getId(), user)).thenReturn(Optional.of(cluster));
            when(exportRepository.findByIdAndCluster(exportId, cluster)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> exportService.getExport(cluster.getId(), exportId, user))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("executeExportAsync")
    class ExecuteExportAsync {

        @Test
        @DisplayName("should succeed on first attempt without retry")
        void shouldSucceedOnFirstAttempt() {
            UUID exportId = UUID.randomUUID();

            ExportService spyService = spy(exportService);
            ReflectionTestUtils.setField(spyService, "maxRetries", 1);
            ReflectionTestUtils.setField(spyService, "retryDelaySeconds", 0);

            doNothing().when(spyService).executeExport(exportId);

            spyService.executeExportAsync(exportId);

            verify(spyService, times(1)).executeExport(exportId);
        }

        @Test
        @DisplayName("should retry and eventually fail after max retries")
        void shouldRetryAndFail() {
            UUID exportId = UUID.randomUUID();
            Export export = createExport(Export.STATUS_PENDING);

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));

            ExportService spyService = spy(exportService);
            ReflectionTestUtils.setField(spyService, "maxRetries", 1);
            ReflectionTestUtils.setField(spyService, "retryDelaySeconds", 0);

            doThrow(new RuntimeException("connection refused")).when(spyService).executeExport(exportId);

            spyService.executeExportAsync(exportId);

            // Should have tried 2 times (initial + 1 retry)
            verify(spyService, times(2)).executeExport(exportId);
            // markExportFailed should set status to FAILED
            assertThat(export.getStatus()).isEqualTo(Export.STATUS_FAILED);
            assertThat(export.getErrorMessage()).contains("connection refused");
        }

        @Test
        @DisplayName("should reset export for retry between attempts")
        void shouldResetForRetry() {
            UUID exportId = UUID.randomUUID();
            Export export = createExport(Export.STATUS_IN_PROGRESS);

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));

            ExportService spyService = spy(exportService);
            ReflectionTestUtils.setField(spyService, "maxRetries", 1);
            ReflectionTestUtils.setField(spyService, "retryDelaySeconds", 0);

            doThrow(new RuntimeException("timeout")).when(spyService).executeExport(exportId);

            spyService.executeExportAsync(exportId);

            // After reset, status should have been set to PENDING then back to FAILED
            // The save should have been called for both reset and markFailed
            verify(exportRepository, atLeast(2)).save(any(Export.class));
        }

        @Test
        @DisplayName("should handle interrupt during retry sleep")
        void shouldHandleInterrupt() {
            UUID exportId = UUID.randomUUID();
            Export export = createExport(Export.STATUS_PENDING);

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));

            ExportService spyService = spy(exportService);
            ReflectionTestUtils.setField(spyService, "maxRetries", 2);
            ReflectionTestUtils.setField(spyService, "retryDelaySeconds", 1);

            doThrow(new RuntimeException("fail")).when(spyService).executeExport(exportId);

            // Set interrupt flag before calling - Thread.sleep will throw InterruptedException
            Thread.currentThread().interrupt();

            spyService.executeExportAsync(exportId);

            // Should have stopped after first retry attempt due to interrupt
            verify(spyService, atMost(2)).executeExport(exportId);
            // Clear interrupted status
            Thread.interrupted();
        }
    }

    @Nested
    @DisplayName("executeExport")
    class ExecuteExport {

        @BeforeEach
        void setUpFields() {
            ReflectionTestUtils.setField(exportService, "exportTimeoutMs", 5000);
            ReflectionTestUtils.setField(exportService, "downloadExpiryHours", 24);
        }

        @Test
        @DisplayName("should throw when export not found")
        void shouldThrowWhenNotFound() {
            UUID exportId = UUID.randomUUID();
            when(exportRepository.findById(exportId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> exportService.executeExport(exportId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Export not found");
        }

        @Test
        @DisplayName("should mark failed and throw when no leader node found")
        void shouldMarkFailedWhenNoLeader() {
            UUID exportId = UUID.randomUUID();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_PENDING);
            export.setCluster(cluster);

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));
            when(patroniService.findLeaderNode(cluster)).thenReturn(null);
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> exportService.executeExport(exportId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("No leader node found");

            assertThat(export.getStatus()).isEqualTo(Export.STATUS_FAILED);
        }

        @Test
        @DisplayName("should mark failed and throw when pg_dump fails")
        void shouldMarkFailedWhenPgDumpFails() {
            UUID exportId = UUID.randomUUID();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_PENDING);
            export.setCluster(cluster);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);
            when(sshService.executeCommand(anyString(), anyString(), anyInt()))
                    .thenReturn(new SshService.CommandResult(1, "", "pg_dump: connection refused"));
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> exportService.executeExport(exportId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("pg_dump failed");

            assertThat(export.getStatus()).isEqualTo(Export.STATUS_FAILED);
        }

        @Test
        @DisplayName("should mark failed when S3 upload fails")
        void shouldMarkFailedWhenUploadFails() {
            UUID exportId = UUID.randomUUID();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_PENDING);
            export.setCluster(cluster);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);
            // pg_dump succeeds, size check succeeds, upload fails
            when(sshService.executeCommand(anyString(), anyString(), anyInt()))
                    .thenReturn(new SshService.CommandResult(0, "", ""))          // pg_dump
                    .thenReturn(new SshService.CommandResult(0, "1048576", ""))   // wc -c size
                    .thenReturn(new SshService.CommandResult(1, "", "upload error")); // curl upload
            when(s3StorageService.generatePresignedPutUrl(anyString(), anyInt()))
                    .thenReturn("https://s3.example.com/put-url");
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> exportService.executeExport(exportId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload export to S3");

            assertThat(export.getStatus()).isEqualTo(Export.STATUS_FAILED);
        }

        @Test
        @DisplayName("should complete successfully when all steps pass")
        void shouldCompleteSuccessfully() {
            UUID exportId = UUID.randomUUID();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_PENDING);
            export.setCluster(cluster);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);
            when(sshService.executeCommand(anyString(), anyString(), anyInt()))
                    .thenReturn(new SshService.CommandResult(0, "", ""))          // pg_dump
                    .thenReturn(new SshService.CommandResult(0, "2097152", ""))   // wc -c size
                    .thenReturn(new SshService.CommandResult(0, "", ""))          // curl upload
                    .thenReturn(new SshService.CommandResult(0, "", ""));         // rm cleanup
            when(s3StorageService.generatePresignedPutUrl(anyString(), anyInt()))
                    .thenReturn("https://s3.example.com/put-url");
            when(s3StorageService.getFileSize(anyString())).thenReturn(2097152L);
            when(s3StorageService.generatePresignedUrl(anyString(), anyInt()))
                    .thenReturn("https://s3.example.com/download-url");
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> inv.getArgument(0));

            exportService.executeExport(exportId);

            assertThat(export.getStatus()).isEqualTo(Export.STATUS_COMPLETED);
            assertThat(export.getDownloadUrl()).isEqualTo("https://s3.example.com/download-url");
            assertThat(export.getSizeBytes()).isEqualTo(2097152L);
            assertThat(export.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should mark failed when file size is zero after dump")
        void shouldMarkFailedWhenFileSizeIsZero() {
            UUID exportId = UUID.randomUUID();
            Cluster cluster = createRunningCluster();
            Export export = createExport(Export.STATUS_PENDING);
            export.setCluster(cluster);

            VpsNode leader = new VpsNode();
            leader.setPublicIp("10.0.0.1");

            when(exportRepository.findById(exportId)).thenReturn(Optional.of(export));
            when(patroniService.findLeaderNode(cluster)).thenReturn(leader);
            when(sshService.executeCommand(anyString(), anyString(), anyInt()))
                    .thenReturn(new SshService.CommandResult(0, "", ""))   // pg_dump success
                    .thenReturn(new SshService.CommandResult(0, "0", "")) // wc -c returns 0
                    .thenReturn(new SshService.CommandResult(0, "No error log found", "")); // cat error log
            when(exportRepository.save(any(Export.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> exportService.executeExport(exportId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Export file is empty");

            assertThat(export.getStatus()).isEqualTo(Export.STATUS_FAILED);
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
                .build();
    }

    private Export createExport(String status) {
        return Export.builder()
                .id(UUID.randomUUID())
                .status(status)
                .format(Export.FORMAT_PG_DUMP)
                .build();
    }
}
