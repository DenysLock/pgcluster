package com.pgcluster.api.event;

import com.pgcluster.api.service.BackupService;
import com.pgcluster.api.service.ExportService;
import com.pgcluster.api.service.ProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles async operation events that should be triggered after transaction commits.
 * Uses @TransactionalEventListener to ensure events are only processed after
 * the originating transaction successfully commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncOperationEventListener {

    private final ProvisioningService provisioningService;
    private final BackupService backupService;
    private final ExportService exportService;

    /**
     * Handle cluster creation - trigger async provisioning.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClusterCreated(ClusterCreatedEvent event) {
        log.debug("Handling ClusterCreatedEvent for cluster: {}", event.getCluster().getSlug());
        provisioningService.provisionClusterAsync(event.getCluster());
    }

    /**
     * Handle cluster deletion request - trigger async deletion.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClusterDeleteRequested(ClusterDeleteRequestedEvent event) {
        log.debug("Handling ClusterDeleteRequestedEvent for cluster: {}", event.getCluster().getSlug());
        provisioningService.deleteClusterAsync(event.getCluster());
    }

    /**
     * Handle backup creation - trigger async backup execution.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBackupCreated(BackupCreatedEvent event) {
        log.debug("Handling BackupCreatedEvent for backup: {}", event.getBackupId());
        backupService.executeBackupAsync(event.getBackupId());
    }

    /**
     * Handle restore request - trigger async restore execution.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRestoreRequested(RestoreRequestedEvent event) {
        log.debug("Handling RestoreRequestedEvent for job: {}, newCluster: {}",
                event.getRestoreJobId(), event.isCreateNewCluster());
        if (event.isCreateNewCluster()) {
            backupService.executeRestoreToNewClusterAsync(event.getRestoreJobId());
        } else {
            backupService.executeRestoreAsync(event.getRestoreJobId());
        }
    }

    /**
     * Handle export creation - trigger async export execution.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExportCreated(ExportCreatedEvent event) {
        log.debug("Handling ExportCreatedEvent for export: {}", event.getExportId());
        exportService.executeExportAsync(event.getExportId());
    }
}
