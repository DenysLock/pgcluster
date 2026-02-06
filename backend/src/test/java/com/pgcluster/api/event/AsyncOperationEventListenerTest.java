package com.pgcluster.api.event;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.service.BackupService;
import com.pgcluster.api.service.ExportService;
import com.pgcluster.api.service.ProvisioningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@DisplayName("AsyncOperationEventListener")
@ExtendWith(MockitoExtension.class)
class AsyncOperationEventListenerTest {

    @Mock private ProvisioningService provisioningService;
    @Mock private BackupService backupService;
    @Mock private ExportService exportService;

    @InjectMocks
    private AsyncOperationEventListener listener;

    @Test
    @DisplayName("should delegate cluster creation to provisioning service")
    void shouldHandleClusterCreated() {
        Cluster cluster = Cluster.builder().slug("test-cluster").build();
        ClusterCreatedEvent event = new ClusterCreatedEvent(this, cluster);

        listener.handleClusterCreated(event);

        verify(provisioningService).provisionClusterAsync(cluster);
    }

    @Test
    @DisplayName("should delegate cluster deletion to provisioning service")
    void shouldHandleClusterDeleteRequested() {
        Cluster cluster = Cluster.builder().slug("test-cluster").build();
        ClusterDeleteRequestedEvent event = new ClusterDeleteRequestedEvent(this, cluster);

        listener.handleClusterDeleteRequested(event);

        verify(provisioningService).deleteClusterAsync(cluster);
    }

    @Test
    @DisplayName("should delegate backup execution to backup service")
    void shouldHandleBackupCreated() {
        UUID backupId = UUID.randomUUID();
        BackupCreatedEvent event = new BackupCreatedEvent(this, backupId);

        listener.handleBackupCreated(event);

        verify(backupService).executeBackupAsync(backupId);
    }

    @Test
    @DisplayName("should delegate restore to new cluster when createNewCluster is true")
    void shouldHandleRestoreToNewCluster() {
        UUID jobId = UUID.randomUUID();
        RestoreRequestedEvent event = new RestoreRequestedEvent(this, jobId, true);

        listener.handleRestoreRequested(event);

        verify(backupService).executeRestoreToNewClusterAsync(jobId);
    }

    @Test
    @DisplayName("should delegate in-place restore when createNewCluster is false")
    void shouldHandleInPlaceRestore() {
        UUID jobId = UUID.randomUUID();
        RestoreRequestedEvent event = new RestoreRequestedEvent(this, jobId, false);

        listener.handleRestoreRequested(event);

        verify(backupService).executeRestoreAsync(jobId);
    }

    @Test
    @DisplayName("should delegate export execution to export service")
    void shouldHandleExportCreated() {
        UUID exportId = UUID.randomUUID();
        ExportCreatedEvent event = new ExportCreatedEvent(this, exportId);

        listener.handleExportCreated(event);

        verify(exportService).executeExportAsync(exportId);
    }
}
