package com.pgcluster.api.service;

import com.pgcluster.api.model.entity.AuditLog;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.ExportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.pgcluster.api.event.ExportCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service for creating database exports (pg_dump).
 * Generates SQL dump files from running clusters, uploads them to S3 storage,
 * and provides time-limited presigned URLs for download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExportRepository exportRepository;
    private final ClusterRepository clusterRepository;
    private final S3StorageService s3StorageService;
    private final SshService sshService;
    private final PatroniService patroniService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    // Self-injection for @Async to work
    @Autowired
    @Lazy
    private ExportService self;

    @Value("${s3.bucket:pgcluster-backups}")
    private String s3Bucket;

    @Value("${export.download-expiry-hours:24}")
    private int downloadExpiryHours;

    @Value("${timeouts.export:3600000}")
    private int exportTimeoutMs;

    @Value("${export.max-retries:2}")
    private int maxRetries;

    @Value("${export.retry-delay-seconds:10}")
    private int retryDelaySeconds;

    /**
     * Create a database export (pg_dump)
     */
    @Transactional
    public Export createExport(UUID clusterId, User user) {
        if (!s3StorageService.isConfigured()) {
            throw new IllegalStateException("Export functionality is not configured. Please configure S3 storage.");
        }

        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        if (!Cluster.STATUS_RUNNING.equals(cluster.getStatus())) {
            throw new IllegalStateException("Cluster must be running to create an export");
        }

        // Check for in-progress exports
        List<Export> inProgressExports = exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(
                cluster, Export.STATUS_IN_PROGRESS);
        List<Export> pendingExports = exportRepository.findByClusterAndStatusOrderByCreatedAtDesc(
                cluster, Export.STATUS_PENDING);

        if (!inProgressExports.isEmpty() || !pendingExports.isEmpty()) {
            throw new IllegalStateException("An export is already in progress for this cluster. Please wait for it to complete.");
        }

        Export export = Export.builder()
                .cluster(cluster)
                .status(Export.STATUS_PENDING)
                .format(Export.FORMAT_PG_DUMP)
                .build();

        export = exportRepository.save(export);
        log.info("Created export {} for cluster {}", export.getId(), cluster.getSlug());

        // Capture IP and user-agent before async call (will be lost in async context)
        String clientIp = auditLogService.getCurrentRequestIp();
        String userAgent = auditLogService.getCurrentRequestUserAgent();

        // Audit log with pre-captured IP and user-agent
        auditLogService.logAsync(AuditLog.EXPORT_INITIATED, user, "export", export.getId(),
                java.util.Map.of(
                        "cluster_id", cluster.getId().toString(),
                        "cluster_name", cluster.getName(),
                        "cluster_slug", cluster.getSlug()
                ), clientIp, userAgent);

        // Publish event to trigger async export after transaction commits
        eventPublisher.publishEvent(new ExportCreatedEvent(this, export.getId()));

        return export;
    }

    /**
     * Execute export asynchronously with auto-retry on transient failures
     */
    @Async
    public void executeExportAsync(UUID exportId) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                executeExport(exportId);
                return; // Success - exit retry loop
            } catch (Exception e) {
                lastException = e;

                if (attempt <= maxRetries) {
                    log.warn("Export {} failed on attempt {}/{}, retrying in {} seconds: {}",
                            exportId, attempt, maxRetries + 1, retryDelaySeconds, e.getMessage());

                    // Reset export status to pending for retry
                    resetExportForRetry(exportId);

                    try {
                        Thread.sleep(retryDelaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Export {} retry interrupted", exportId);
                        break;
                    }
                } else {
                    log.error("Export {} failed after {} attempts: {}",
                            exportId, attempt, e.getMessage(), e);
                }
            }
        }

        // All retries exhausted
        if (lastException != null) {
            markExportFailed(exportId, lastException.getMessage());
        }
    }

    /**
     * Reset export status for retry attempt
     */
    private void resetExportForRetry(UUID exportId) {
        exportRepository.findById(exportId).ifPresent(export -> {
            export.setStatus(Export.STATUS_PENDING);
            export.setErrorMessage(null);
            export.setStartedAt(null);
            exportRepository.save(export);
        });
    }

    /**
     * Main export execution logic using pg_dump
     */
    @Transactional
    public void executeExport(UUID exportId) {
        Export export = exportRepository.findById(exportId)
                .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));

        Cluster cluster = export.getCluster();
        log.info("Starting export execution for cluster {} (export {})", cluster.getSlug(), exportId);

        // Update status to in_progress
        export.setStatus(Export.STATUS_IN_PROGRESS);
        export.setStartedAt(Instant.now());
        exportRepository.save(export);

        try {
            // Find leader node
            VpsNode leaderNode = patroniService.findLeaderNode(cluster);
            if (leaderNode == null) {
                throw new RuntimeException("No leader node found for cluster " + cluster.getSlug());
            }

            log.info("Found leader node: {} ({})", leaderNode.getName(), leaderNode.getPublicIp());

            // Generate S3 path for the export
            String timestamp = Instant.now().toString().replace(":", "-").replace(".", "-");
            String s3Path = String.format("exports/%s/%s_%s.sql.gz", cluster.getId(), cluster.getSlug(), timestamp);
            export.setS3Path(s3Path);

            // Execute pg_dump with piped gzip (use pipefail to catch pg_dump errors)
            // --no-owner and --no-privileges ensure portability to other DBaaS providers
            String pgDumpCommand = "docker exec patroni bash -c 'set -o pipefail; pg_dump -U postgres -h localhost -Fp --no-owner --no-privileges postgres 2>/tmp/pg_dump_err.log | gzip > /tmp/export.sql.gz || { cat /tmp/pg_dump_err.log; exit 1; }'";

            SshService.CommandResult dumpResult = sshService.executeCommand(
                    leaderNode.getPublicIp(),
                    pgDumpCommand,
                    exportTimeoutMs
            );

            log.info("pg_dump result - exitCode: {}, stdout: '{}', stderr: '{}'",
                    dumpResult.getExitCode(),
                    dumpResult.getStdout().length() > 200 ? dumpResult.getStdout().substring(0, 200) + "..." : dumpResult.getStdout(),
                    dumpResult.getStderr());

            if (!dumpResult.isSuccess()) {
                throw new RuntimeException("pg_dump failed: " + dumpResult.getStderr() + " | stdout: " + dumpResult.getStdout());
            }

            // Get file size (run inside container where the file is)
            SshService.CommandResult sizeResult = sshService.executeCommand(
                    leaderNode.getPublicIp(),
                    "docker exec patroni sh -c 'wc -c < /tmp/export.sql.gz' 2>/dev/null || echo 0",
                    30000
            );

            log.info("stat result - exitCode: {}, stdout: '{}', stderr: '{}'",
                    sizeResult.getExitCode(), sizeResult.getStdout(), sizeResult.getStderr());

            if (sizeResult.isSuccess() && !sizeResult.getStdout().isBlank()) {
                try {
                    export.setSizeBytes(Long.parseLong(sizeResult.getStdout().trim()));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse file size: {}", sizeResult.getStdout());
                }
            }

            // Validate file is not empty
            if (export.getSizeBytes() == null || export.getSizeBytes() == 0) {
                // Try to get the actual pg_dump error from the error log file (inside container)
                SshService.CommandResult errResult = sshService.executeCommand(
                        leaderNode.getPublicIp(),
                        "docker exec patroni sh -c 'cat /tmp/pg_dump_err.log 2>/dev/null || echo No error log found'",
                        10000
                );
                String pgDumpError = errResult.getStdout();
                throw new RuntimeException("Export file is empty. pg_dump error: " + pgDumpError);
            }

            // Upload directly to S3 from container using presigned URL (streaming - no memory loading)
            log.info("Uploading export to S3: {}", s3Path);
            String presignedPutUrl = s3StorageService.generatePresignedPutUrl(s3Path, 60); // 60 minute expiry

            // Use curl on the remote server to upload directly to S3
            // This avoids loading the entire file into API server memory
            String uploadCommand = String.format(
                    "docker exec patroni sh -c 'curl -s -X PUT -T /tmp/export.sql.gz \"%s\"'",
                    presignedPutUrl.replace("\"", "\\\"")
            );

            SshService.CommandResult uploadResult = sshService.executeCommand(
                    leaderNode.getPublicIp(),
                    uploadCommand,
                    exportTimeoutMs
            );

            if (!uploadResult.isSuccess()) {
                throw new RuntimeException("Failed to upload export to S3: " + uploadResult.getStderr());
            }

            // Verify upload and get file size from S3
            long fileSize = s3StorageService.getFileSize(s3Path);
            if (fileSize <= 0) {
                throw new RuntimeException("Export upload verification failed - file not found in S3");
            }

            log.info("Export uploaded to S3: {} ({} bytes)", s3Path, fileSize);
            export.setSizeBytes(fileSize);

            // Generate presigned download URL
            Instant expiresAt = Instant.now().plus(downloadExpiryHours, ChronoUnit.HOURS);
            String downloadUrl = s3StorageService.generatePresignedUrl(s3Path, downloadExpiryHours);
            export.setDownloadUrl(downloadUrl);
            export.setDownloadExpiresAt(expiresAt);

            // Clean up temp file
            sshService.executeCommand(leaderNode.getPublicIp(), "rm -f /tmp/export.sql.gz", 10000);

            // Mark as completed
            export.setStatus(Export.STATUS_COMPLETED);
            export.setCompletedAt(Instant.now());
            exportRepository.save(export);

            log.info("Export {} completed successfully. Size: {} bytes", exportId, export.getSizeBytes());

        } catch (Exception e) {
            log.error("Export execution failed: {}", e.getMessage(), e);
            export.setStatus(Export.STATUS_FAILED);
            export.setErrorMessage(e.getMessage());
            exportRepository.save(export);
            throw e;
        }
    }

    private void markExportFailed(UUID exportId, String errorMessage) {
        exportRepository.findById(exportId).ifPresent(export -> {
            export.setStatus(Export.STATUS_FAILED);
            export.setErrorMessage(errorMessage);
            exportRepository.save(export);
        });
    }

    /**
     * List exports for a cluster
     */
    @Transactional(readOnly = true)
    public List<Export> listExports(UUID clusterId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        return exportRepository.findByClusterOrderByCreatedAtDesc(cluster);
    }

    /**
     * Get a specific export
     */
    @Transactional(readOnly = true)
    public Export getExport(UUID clusterId, UUID exportId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        return exportRepository.findByIdAndCluster(exportId, cluster)
                .orElseThrow(() -> new IllegalArgumentException("Export not found"));
    }

    /**
     * Refresh download URL for an export (generate new presigned URL)
     */
    @Transactional
    public Export refreshDownloadUrl(UUID clusterId, UUID exportId, User user) {
        Export export = getExport(clusterId, exportId, user);

        if (!Export.STATUS_COMPLETED.equals(export.getStatus())) {
            throw new IllegalStateException("Can only refresh download URL for completed exports");
        }

        if (export.getS3Path() == null) {
            throw new IllegalStateException("Export has no S3 path");
        }

        Instant expiresAt = Instant.now().plus(downloadExpiryHours, ChronoUnit.HOURS);
        String downloadUrl = s3StorageService.generatePresignedUrl(export.getS3Path(), downloadExpiryHours);
        export.setDownloadUrl(downloadUrl);
        export.setDownloadExpiresAt(expiresAt);

        return exportRepository.save(export);
    }

    /**
     * Delete an export and its S3 file
     */
    @Transactional
    public void deleteExport(UUID clusterId, UUID exportId, User user) {
        // Capture IP and user-agent before async audit log
        String clientIp = auditLogService.getCurrentRequestIp();
        String userAgent = auditLogService.getCurrentRequestUserAgent();

        Export export = getExport(clusterId, exportId, user);

        // Don't allow deleting in-progress exports
        if (Export.STATUS_IN_PROGRESS.equals(export.getStatus())) {
            throw new IllegalStateException("Cannot delete an export that is in progress");
        }

        // Delete from S3 if path exists
        if (export.getS3Path() != null && s3StorageService.isConfigured()) {
            try {
                s3StorageService.deleteFile(export.getS3Path());
                log.info("Deleted export file from S3: {}", export.getS3Path());
            } catch (Exception e) {
                log.warn("Failed to delete export file from S3: {}. Continuing with database deletion.", e.getMessage());
            }
        }

        // Audit log with pre-captured IP and user-agent
        Cluster cluster = export.getCluster();
        auditLogService.logAsync(AuditLog.EXPORT_DELETED, user, "export", exportId,
                java.util.Map.of(
                        "cluster_id", cluster.getId().toString(),
                        "cluster_name", cluster.getName(),
                        "cluster_slug", cluster.getSlug()
                ), clientIp, userAgent);

        exportRepository.delete(export);
        log.info("Deleted export {} for cluster {}", exportId, clusterId);
    }

    /**
     * Delete an export as admin (bypasses user ownership check)
     */
    @Transactional
    public void deleteExportAsAdmin(Cluster cluster, Export export) {
        // Don't allow deleting in-progress exports
        if (Export.STATUS_IN_PROGRESS.equals(export.getStatus())) {
            throw new IllegalStateException("Cannot delete an export that is in progress");
        }

        // Delete from S3 if path exists
        if (export.getS3Path() != null && s3StorageService.isConfigured()) {
            try {
                s3StorageService.deleteFile(export.getS3Path());
                log.info("Deleted export file from S3: {}", export.getS3Path());
            } catch (Exception e) {
                log.warn("Failed to delete export file from S3: {}. Continuing with database deletion.", e.getMessage());
            }
        }

        exportRepository.delete(export);
        log.info("Admin deleted export {} for cluster {}", export.getId(), cluster.getSlug());
    }
}
