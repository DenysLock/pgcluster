package com.pgcluster.api.service;

import com.pgcluster.api.model.dto.PgBackRestBackupInfo;
import com.pgcluster.api.model.dto.RestoreRequest;
import com.pgcluster.api.util.FormatUtils;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.RestoreJob;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.model.entity.VpsNode;
import com.pgcluster.api.repository.BackupRepository;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.RestoreJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import com.pgcluster.api.event.BackupCreatedEvent;
import com.pgcluster.api.event.RestoreRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.pgcluster.api.util.PasswordGenerator;

/**
 * Service for managing PostgreSQL cluster backups and restores.
 * Integrates with pgBackRest for full, differential, and incremental backups
 * with Point-in-Time Recovery (PITR) support. Also handles backup scheduling,
 * retention policies, and restore operations to existing or new clusters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupRepository backupRepository;
    private final RestoreJobRepository restoreJobRepository;
    private final ClusterRepository clusterRepository;
    private final S3StorageService s3StorageService;
    private final PgBackRestService pgBackRestService;
    private final ProvisioningService provisioningService;
    private final PatroniService patroniService;
    private final ApplicationEventPublisher eventPublisher;

    // Self-injection to enable @Async to work (Spring proxy requirement)
    @Autowired
    @Lazy
    private BackupService self;

    @Value("${backup.enabled:false}")
    private boolean backupEnabled;

    @Value("${backup.retention.daily:7}")
    private int retentionDaily;

    @Value("${backup.retention.weekly:4}")
    private int retentionWeekly;

    @Value("${backup.retention.monthly:12}")
    private int retentionMonthly;

    /**
     * Create a manual backup for a cluster (defaults to incremental)
     */
    @Transactional
    public Backup createBackup(UUID clusterId, User user) {
        return createBackup(clusterId, user, null);
    }

    /**
     * Create a manual backup for a cluster with specified backup type
     *
     * @param clusterId The cluster ID
     * @param user The authenticated user
     * @param requestedBackupType The requested backup type: "full", "diff", "incr" (null defaults to "incr")
     */
    @Transactional
    public Backup createBackup(UUID clusterId, User user, String requestedBackupType) {
        if (!s3StorageService.isConfigured()) {
            throw new IllegalStateException("Backup functionality is not configured. Please configure S3 storage.");
        }

        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        if (!Cluster.STATUS_RUNNING.equals(cluster.getStatus())) {
            throw new IllegalStateException("Cluster must be running to create a backup");
        }

        // Check for in-progress or pending backups to prevent concurrent backup corruption
        List<Backup> inProgressBackups = backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_IN_PROGRESS);
        List<Backup> pendingBackups = backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_PENDING);
        if (!inProgressBackups.isEmpty() || !pendingBackups.isEmpty()) {
            throw new IllegalStateException("A backup is already in progress for this cluster. Please wait for it to complete.");
        }

        // Validate and normalize requested backup type
        String normalizedType = normalizeBackupType(requestedBackupType);

        Backup backup = Backup.builder()
                .cluster(cluster)
                .type(Backup.TYPE_MANUAL)
                .status(Backup.STATUS_PENDING)
                .currentStep(Backup.STEP_PENDING)
                .progressPercent(0)
                .retentionType(Backup.RETENTION_MANUAL)
                .requestedBackupType(normalizedType)
                .build();

        backup = backupRepository.save(backup);
        log.info("Created manual {} backup {} for cluster {}", normalizedType, backup.getId(), cluster.getSlug());

        // Publish event to trigger async backup after transaction commits
        eventPublisher.publishEvent(new BackupCreatedEvent(this, backup.getId()));

        return backup;
    }

    /**
     * Normalize and validate backup type input
     */
    private String normalizeBackupType(String backupType) {
        if (backupType == null || backupType.isBlank()) {
            return Backup.BACKUP_TYPE_INCR; // Default to incremental
        }
        String normalized = backupType.toLowerCase().trim();
        return switch (normalized) {
            case "full" -> Backup.BACKUP_TYPE_FULL;
            case "diff", "differential" -> Backup.BACKUP_TYPE_DIFF;
            case "incr", "incremental" -> Backup.BACKUP_TYPE_INCR;
            default -> throw new IllegalArgumentException(
                    "Invalid backup type: " + backupType + ". Valid types: full, diff, incr");
        };
    }

    /**
     * Execute backup asynchronously
     */
    @Async
    public void executeBackupAsync(UUID backupId) {
        try {
            executeBackup(backupId);
        } catch (Exception e) {
            log.error("Failed to execute backup {}: {}", backupId, e.getMessage(), e);
            markBackupFailed(backupId, e.getMessage());
        }
    }

    /**
     * Main backup execution logic using pgBackRest
     */
    @Transactional
    public void executeBackup(UUID backupId) {
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found: " + backupId));

        Cluster cluster = backup.getCluster();
        log.info("Starting backup execution for cluster {} (backup {})", cluster.getSlug(), backupId);

        // Update status to in_progress - PREPARING step
        backup.setStatus(Backup.STATUS_IN_PROGRESS);
        backup.setCurrentStep(Backup.STEP_PREPARING);
        backup.setProgressPercent(10);
        backup.setStartedAt(Instant.now());
        backupRepository.save(backup);

        try {
            // Find leader node
            VpsNode leaderNode = patroniService.findLeaderNode(cluster);
            if (leaderNode == null) {
                throw new RuntimeException("No leader node found for cluster " + cluster.getSlug());
            }

            log.info("Found leader node: {} ({})", leaderNode.getName(), leaderNode.getPublicIp());

            // Set S3 paths for pgBackRest storage
            String basePath = String.format("pgbackrest/%s", cluster.getId());
            String walPath = String.format("pgbackrest/%s/archive/%s", cluster.getId(), cluster.getSlug());
            backup.setS3BasePath(basePath);
            backup.setS3WalPath(walPath);

            // Update to BACKING_UP step
            backup.setCurrentStep(Backup.STEP_BACKING_UP);
            backup.setProgressPercent(30);
            backupRepository.save(backup);

            // Determine backup type: use requestedBackupType if set, otherwise infer from schedule
            // Note: pgBackRest auto-promotes to full if no prior backup exists
            PgBackRestBackupInfo result;
            String effectiveType = determineEffectiveBackupType(backup);

            result = switch (effectiveType) {
                case Backup.BACKUP_TYPE_FULL -> {
                    log.info("Executing full backup for cluster {}", cluster.getSlug());
                    yield pgBackRestService.executeFullBackup(cluster, leaderNode);
                }
                case Backup.BACKUP_TYPE_DIFF -> {
                    log.info("Executing differential backup for cluster {}", cluster.getSlug());
                    yield pgBackRestService.executeDifferentialBackup(cluster, leaderNode);
                }
                default -> {
                    log.info("Executing incremental backup for cluster {}", cluster.getSlug());
                    yield pgBackRestService.executeIncrementalBackup(cluster, leaderNode);
                }
            };

            // Set actual backup type from pgBackRest result (may differ from requested)
            backup.setBackupType(mapPgBackRestType(result.getType()));

            // Update to UPLOADING step (pgBackRest handles upload during backup)
            backup.setCurrentStep(Backup.STEP_UPLOADING);
            backup.setProgressPercent(70);
            backupRepository.save(backup);

            // Update backup metadata from pgBackRest result
            backup.setPgbackrestLabel(result.getLabel());
            backup.setSizeBytes(result.getBackupSizeBytes() != null ? result.getBackupSizeBytes() : 0L);
            backup.setWalStartLsn(result.getWalStartLsn());
            backup.setWalEndLsn(result.getWalStopLsn());

            // Update to VERIFYING step
            backup.setCurrentStep(Backup.STEP_VERIFYING);
            backup.setProgressPercent(90);
            backupRepository.save(backup);

            // Set recovery time window
            backup.setEarliestRecoveryTime(result.getStartTime() != null ? result.getStartTime() : backup.getStartedAt());
            backup.setLatestRecoveryTime(result.getStopTime() != null ? result.getStopTime() : Instant.now());

            // Calculate expiration based on retention type
            backup.setExpiresAt(calculateExpirationTime(backup.getRetentionType()));

            // Mark as completed
            backup.setStatus(Backup.STATUS_COMPLETED);
            backup.setCurrentStep(Backup.STEP_COMPLETED);
            backup.setProgressPercent(100);
            backup.setCompletedAt(Instant.now());
            backupRepository.save(backup);

            log.info("Backup {} completed successfully. Type: {}, Label: {}, Size: {} bytes",
                    backupId, backup.getBackupType(), backup.getPgbackrestLabel(), backup.getSizeBytes());

        } catch (Exception e) {
            log.error("Backup execution failed: {}", e.getMessage(), e);
            backup.setStatus(Backup.STATUS_FAILED);
            backup.setCurrentStep(Backup.STEP_FAILED);
            backup.setErrorMessage(e.getMessage());
            backupRepository.save(backup);
            throw e;
        }
    }

    /**
     * Check if this is a weekly scheduled backup
     */
    private boolean isWeeklyBackup(Backup backup) {
        return Backup.TYPE_SCHEDULED_WEEKLY.equals(backup.getType()) ||
               Backup.TYPE_SCHEDULED_MONTHLY.equals(backup.getType());
    }

    /**
     * Check if this is a daily scheduled backup
     */
    private boolean isDailyBackup(Backup backup) {
        return Backup.TYPE_SCHEDULED_DAILY.equals(backup.getType());
    }

    /**
     * Determine the effective backup type to use.
     * Priority: requestedBackupType > schedule-based inference > default (incr)
     */
    private String determineEffectiveBackupType(Backup backup) {
        // If user explicitly requested a type, use it
        if (backup.getRequestedBackupType() != null && !backup.getRequestedBackupType().isBlank()) {
            return backup.getRequestedBackupType();
        }

        // Fall back to schedule-based logic for scheduled backups
        if (isWeeklyBackup(backup)) {
            return Backup.BACKUP_TYPE_FULL;
        } else if (isDailyBackup(backup)) {
            return Backup.BACKUP_TYPE_DIFF;
        }

        // Default to incremental for manual backups without explicit type
        return Backup.BACKUP_TYPE_INCR;
    }

    /**
     * Map pgBackRest backup type to our Backup type constants.
     * pgBackRest returns lowercase types: "full", "diff", "incr"
     */
    private String mapPgBackRestType(String pgBackRestType) {
        if (pgBackRestType == null) {
            return Backup.BACKUP_TYPE_FULL;
        }
        return switch (pgBackRestType.toLowerCase()) {
            case "full" -> Backup.BACKUP_TYPE_FULL;
            case "diff" -> Backup.BACKUP_TYPE_DIFF;
            case "incr" -> Backup.BACKUP_TYPE_INCR;
            default -> Backup.BACKUP_TYPE_FULL;
        };
    }

    /**
     * Calculate expiration time based on retention type
     */
    private Instant calculateExpirationTime(String retentionType) {
        return switch (retentionType) {
            case Backup.RETENTION_DAILY -> Instant.now().plus(retentionDaily, ChronoUnit.DAYS);
            case Backup.RETENTION_WEEKLY -> Instant.now().plus(retentionWeekly * 7L, ChronoUnit.DAYS);
            case Backup.RETENTION_MONTHLY -> Instant.now().plus(retentionMonthly * 30L, ChronoUnit.DAYS);
            case Backup.RETENTION_MANUAL -> null; // Manual backups don't expire automatically
            default -> null;
        };
    }

    private void markBackupFailed(UUID backupId, String errorMessage) {
        backupRepository.findById(backupId).ifPresent(backup -> {
            backup.setStatus(Backup.STATUS_FAILED);
            backup.setCurrentStep(Backup.STEP_FAILED);
            backup.setErrorMessage(errorMessage);
            backupRepository.save(backup);
        });
    }

    /**
     * List backups for a cluster.
     * By default, excludes deleted backups.
     *
     * @param clusterId The cluster ID
     * @param user The authenticated user
     * @param includeDeleted If true, includes deleted backups (for admin/debug)
     * @return List of backups
     */
    @Transactional(readOnly = true)
    public List<Backup> listBackups(UUID clusterId, User user, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        List<Backup> backups = backupRepository.findByClusterOrderByCreatedAtDesc(cluster);

        if (!includeDeleted) {
            backups = backups.stream()
                    .filter(b -> !Backup.STATUS_DELETED.equals(b.getStatus()))
                    .toList();
        }

        return backups;
    }

    /**
     * List backups for a cluster (excludes deleted by default)
     */
    @Transactional(readOnly = true)
    public List<Backup> listBackups(UUID clusterId, User user) {
        return listBackups(clusterId, user, false);
    }

    /**
     * Get a specific backup
     */
    @Transactional(readOnly = true)
    public Backup getBackup(UUID clusterId, UUID backupId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        return backupRepository.findByIdAndCluster(backupId, cluster)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found"));
    }

    /**
     * Get information about what will be deleted if this backup is deleted.
     * Used to show a confirmation dialog to the user.
     */
    @Transactional(readOnly = true)
    public com.pgcluster.api.model.dto.BackupDeletionInfo getBackupDeletionInfo(UUID clusterId, UUID backupId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        Backup backup = backupRepository.findByIdAndCluster(backupId, cluster)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found"));

        if (Backup.STATUS_DELETED.equals(backup.getStatus())) {
            throw new IllegalStateException("This backup is already deleted");
        }

        List<Backup> dependentBackups = findDependentBackups(cluster, backup);
        return com.pgcluster.api.model.dto.BackupDeletionInfo.from(backup, dependentBackups);
    }

    /**
     * Delete a backup and its dependents using pgBackRest.
     * Requires cluster to be running for SSH access.
     *
     * @param clusterId The cluster ID
     * @param backupId The backup ID to delete
     * @param user The authenticated user
     * @param confirmed Whether the user has confirmed cascade deletion
     */
    @Transactional
    public void deleteBackup(UUID clusterId, UUID backupId, User user, boolean confirmed) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        Backup backup = backupRepository.findByIdAndCluster(backupId, cluster)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found"));

        if (Backup.STATUS_DELETED.equals(backup.getStatus())) {
            throw new IllegalStateException("This backup is already deleted");
        }

        // Check cluster is running (need SSH access)
        if (!Cluster.STATUS_RUNNING.equals(cluster.getStatus())) {
            throw new IllegalStateException("Cluster must be running to delete backups. Current status: " + cluster.getStatus());
        }

        // Check if this is the only full backup - pgBackRest won't allow deleting it
        if (Backup.BACKUP_TYPE_FULL.equals(backup.getBackupType())) {
            List<Backup> allFullBackups = backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(cluster, Backup.STATUS_COMPLETED)
                    .stream()
                    .filter(b -> Backup.BACKUP_TYPE_FULL.equals(b.getBackupType()))
                    .toList();

            if (allFullBackups.size() <= 1) {
                throw new IllegalStateException(
                        "Cannot delete the only full backup. pgBackRest requires at least one full backup " +
                        "to exist in the repository. Create a new full backup first, then you can delete this one."
                );
            }
        }

        // Find dependent backups
        List<Backup> dependentBackups = findDependentBackups(cluster, backup);

        // If there are dependents and not confirmed, reject
        if (!dependentBackups.isEmpty() && !confirmed) {
            throw new IllegalStateException(
                    "This backup has " + dependentBackups.size() + " dependent backup(s). " +
                    "Use confirm=true to delete all, or call GET /deletion-info first to see details."
            );
        }

        // Find leader node for SSH
        VpsNode leaderNode = patroniService.findLeaderNode(cluster);
        if (leaderNode == null) {
            throw new RuntimeException("No leader node found for cluster " + cluster.getSlug());
        }

        // Delete from pgBackRest (this handles chain deletion properly)
        String pgbackrestLabel = backup.getPgbackrestLabel();
        if (pgbackrestLabel != null && !pgbackrestLabel.isBlank()) {
            try {
                log.info("Deleting backup {} ({}) from pgBackRest", backupId, pgbackrestLabel);
                pgBackRestService.expireSpecificBackup(cluster, leaderNode, pgbackrestLabel);
                log.info("Backup {} deleted from pgBackRest", pgbackrestLabel);
            } catch (Exception e) {
                log.error("Failed to delete backup from pgBackRest: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to delete backup from storage: " + e.getMessage(), e);
            }
        }

        // Mark the primary backup as deleted in DB
        backup.setStatus(Backup.STATUS_DELETED);
        backupRepository.save(backup);
        log.info("Backup {} marked as deleted in database", backupId);

        // Mark all dependent backups as deleted in DB
        for (Backup dependent : dependentBackups) {
            dependent.setStatus(Backup.STATUS_DELETED);
            backupRepository.save(dependent);
            log.info("Dependent backup {} marked as deleted", dependent.getId());
        }

        log.info("Backup deletion completed: {} primary + {} dependents deleted",
                1, dependentBackups.size());
    }

    /**
     * Find backups that depend on a given full backup
     */
    private List<Backup> findDependentBackups(Cluster cluster, Backup fullBackup) {
        if (fullBackup.getPgbackrestLabel() == null) {
            return List.of();
        }

        // Get all completed backups for the cluster
        List<Backup> allBackups = backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(
                cluster, Backup.STATUS_COMPLETED);

        // Find diff/incr backups created after this full backup but before the next full backup
        Instant fullBackupTime = fullBackup.getCreatedAt();
        Instant nextFullBackupTime = allBackups.stream()
                .filter(b -> Backup.BACKUP_TYPE_FULL.equals(b.getBackupType()))
                .filter(b -> b.getCreatedAt().isAfter(fullBackupTime))
                .map(Backup::getCreatedAt)
                .min(Instant::compareTo)
                .orElse(Instant.MAX);

        return allBackups.stream()
                .filter(b -> !b.getId().equals(fullBackup.getId()))
                .filter(b -> !Backup.BACKUP_TYPE_FULL.equals(b.getBackupType()))
                .filter(b -> b.getCreatedAt().isAfter(fullBackupTime))
                .filter(b -> b.getCreatedAt().isBefore(nextFullBackupTime))
                .filter(b -> !Backup.STATUS_DELETED.equals(b.getStatus()))
                .toList();
    }

    /**
     * Initiate restore from backup to a new cluster
     */
    @Transactional
    public RestoreJob restoreBackup(UUID clusterId, UUID backupId, RestoreRequest request, User user) {
        Cluster sourceCluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        Backup backup = backupRepository.findByIdAndCluster(backupId, sourceCluster)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found"));

        if (!Backup.STATUS_COMPLETED.equals(backup.getStatus())) {
            throw new IllegalStateException("Cannot restore from a backup that is not completed");
        }

        // Check for concurrent restore jobs on the source cluster
        List<RestoreJob> pendingJobs = restoreJobRepository.findPendingJobsForCluster(sourceCluster.getId());
        if (!pendingJobs.isEmpty()) {
            throw new IllegalStateException("A restore operation is already in progress for this cluster. Please wait for it to complete.");
        }

        // Extract values from request (or use defaults)
        Instant targetTime = request != null ? request.getTargetTime() : null;
        boolean createNewCluster = request == null || request.isCreateNewCluster(); // Default to true for safety
        String newClusterName = request != null ? request.getNewClusterName() : null;

        // Validate PITR target time if provided
        String restoreType = RestoreJob.TYPE_FULL;
        if (targetTime != null) {
            if (backup.getEarliestRecoveryTime() != null && targetTime.isBefore(backup.getEarliestRecoveryTime())) {
                throw new IllegalArgumentException("Target time is before the earliest recovery time");
            }
            if (backup.getLatestRecoveryTime() != null && targetTime.isAfter(backup.getLatestRecoveryTime())) {
                throw new IllegalArgumentException("Target time is after the latest recovery time");
            }
            restoreType = RestoreJob.TYPE_PITR;
        }

        // Create new cluster if requested (default behavior)
        Cluster targetCluster = null;
        if (createNewCluster) {
            if (newClusterName == null || newClusterName.isBlank()) {
                // Generate default name if not provided
                String dateStr = java.time.LocalDate.now().toString().replace("-", "");
                newClusterName = sourceCluster.getSlug() + "-restored-" + dateStr;
            }

            // Generate unique slug and credentials for new cluster
            String slug = generateUniqueSlug(newClusterName);
            String postgresPassword = PasswordGenerator.generate(24);

            targetCluster = Cluster.builder()
                    .user(user)
                    .name(newClusterName)
                    .slug(slug)
                    .plan(sourceCluster.getPlan())
                    .status(Cluster.STATUS_PENDING)
                    .postgresVersion(sourceCluster.getPostgresVersion())
                    .nodeCount(sourceCluster.getNodeCount())
                    .nodeSize(sourceCluster.getNodeSize())
                    .region(sourceCluster.getRegion())
                    .postgresPassword(postgresPassword)
                    .provisioningStep(Cluster.STEP_CREATING_SERVERS)
                    .provisioningProgress(1)
                    .build();

            targetCluster = clusterRepository.save(targetCluster);
            log.info("Created target cluster for restore: {} ({})", targetCluster.getName(), targetCluster.getSlug());
        }

        // Create restore job
        RestoreJob restoreJob = RestoreJob.builder()
                .sourceCluster(sourceCluster)
                .targetCluster(targetCluster)
                .backup(backup)
                .restoreType(restoreType)
                .targetTime(targetTime)
                .status(RestoreJob.STATUS_PENDING)
                .progress(0)
                .build();

        restoreJob = restoreJobRepository.save(restoreJob);
        log.info("Created restore job {} for backup {} (type: {}, newCluster: {})",
                restoreJob.getId(), backupId, restoreType, createNewCluster);

        // Publish event to trigger async restore after transaction commits
        eventPublisher.publishEvent(new RestoreRequestedEvent(this, restoreJob.getId(), createNewCluster));

        return restoreJob;
    }

    /**
     * Generate a unique cluster slug
     */
    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // Add random suffix to ensure uniqueness
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        return baseSlug + "-" + randomSuffix;
    }


    /**
     * Execute restore job asynchronously (in-place restore)
     */
    @Async
    public void executeRestoreAsync(UUID restoreJobId) {
        try {
            executeRestore(restoreJobId);
        } catch (Exception e) {
            log.error("Failed to execute restore job {}: {}", restoreJobId, e.getMessage(), e);
            markRestoreJobFailed(restoreJobId, e.getMessage());
        }
    }

    /**
     * Execute restore to new cluster asynchronously
     */
    @Async
    public void executeRestoreToNewClusterAsync(UUID restoreJobId) {
        try {
            executeRestoreToNewCluster(restoreJobId);
        } catch (Exception e) {
            log.error("Failed to execute restore to new cluster {}: {}", restoreJobId, e.getMessage(), e);
            markRestoreJobFailed(restoreJobId, e.getMessage());
        }
    }

    /**
     * Execute restore to a new cluster using ProvisioningService
     */
    @Transactional
    public void executeRestoreToNewCluster(UUID restoreJobId) {
        RestoreJob job = restoreJobRepository.findById(restoreJobId)
                .orElseThrow(() -> new IllegalArgumentException("Restore job not found: " + restoreJobId));

        Cluster sourceCluster = job.getSourceCluster();
        Cluster targetCluster = job.getTargetCluster();
        Backup backup = job.getBackup();

        if (targetCluster == null) {
            throw new IllegalStateException("Target cluster is null for restore job " + restoreJobId);
        }

        log.info("Starting restore to new cluster: {} -> {} (job {})",
                sourceCluster.getSlug(), targetCluster.getSlug(), restoreJobId);

        // Update job status
        job.setStatus(RestoreJob.STATUS_IN_PROGRESS);
        job.setCurrentStep(RestoreJob.STEP_CREATING_CLUSTER);
        job.setProgress(5);
        restoreJobRepository.save(job);

        try {
            // Delegate to ProvisioningService for the heavy lifting
            provisioningService.provisionClusterFromRestore(
                    targetCluster,
                    sourceCluster,
                    backup,
                    job.getTargetTime(),
                    job  // Pass job for progress updates
            );

            // Mark job as completed
            job.setStatus(RestoreJob.STATUS_COMPLETED);
            job.setProgress(100);
            job.setCompletedAt(Instant.now());
            restoreJobRepository.save(job);

            log.info("Restore to new cluster {} completed successfully", targetCluster.getSlug());

        } catch (Exception e) {
            log.error("Restore to new cluster failed: {}", e.getMessage(), e);
            job.setStatus(RestoreJob.STATUS_FAILED);
            job.setErrorMessage(e.getMessage());
            restoreJobRepository.save(job);

            // Also mark target cluster as error
            targetCluster.setStatus(Cluster.STATUS_ERROR);
            targetCluster.setErrorMessage("Restore failed: " + e.getMessage());
            clusterRepository.save(targetCluster);

            throw new RuntimeException("Restore to new cluster failed: " + e.getMessage(), e);
        }
    }

    /**
     * Main restore execution logic using pgBackRest
     * Restores to the same cluster (in-place restore)
     */
    @Transactional
    public void executeRestore(UUID restoreJobId) {
        RestoreJob job = restoreJobRepository.findById(restoreJobId)
                .orElseThrow(() -> new IllegalArgumentException("Restore job not found: " + restoreJobId));

        Cluster sourceCluster = job.getSourceCluster();
        Backup backup = job.getBackup();

        log.info("Starting restore for cluster {} from backup {} (job {})",
                sourceCluster.getSlug(), backup.getId(), restoreJobId);

        // Update status
        job.setStatus(RestoreJob.STATUS_IN_PROGRESS);
        job.setCurrentStep(RestoreJob.STEP_CREATING_CLUSTER);
        job.setProgress(10);
        restoreJobRepository.save(job);

        try {
            // Find leader node for restore
            VpsNode leaderNode = patroniService.findLeaderNode(sourceCluster);
            if (leaderNode == null) {
                throw new RuntimeException("No leader node found for cluster " + sourceCluster.getSlug());
            }

            // Update progress: preparing restore
            job.setCurrentStep("PREPARING_RESTORE");
            job.setProgress(20);
            restoreJobRepository.save(job);

            // Execute pgBackRest restore
            log.info("Executing pgBackRest restore from backup {} (label: {})",
                    backup.getId(), backup.getPgbackrestLabel());

            job.setCurrentStep("RESTORING_DATA");
            job.setProgress(40);
            restoreJobRepository.save(job);

            // Execute the restore using pgBackRest
            pgBackRestService.executeRestore(
                    sourceCluster,
                    leaderNode,
                    backup.getPgbackrestLabel(),
                    job.getTargetTime()
            );

            // Update progress: restore complete
            job.setCurrentStep("VERIFYING_RESTORE");
            job.setProgress(80);
            restoreJobRepository.save(job);

            // Wait for PostgreSQL to be ready with polling
            log.info("Waiting for PostgreSQL to recover...");
            waitForPostgresReady(leaderNode, 30); // 30 seconds max

            // Mark as completed
            job.setStatus(RestoreJob.STATUS_COMPLETED);
            job.setProgress(100);
            job.setCompletedAt(Instant.now());
            restoreJobRepository.save(job);

            log.info("Restore job {} completed successfully", restoreJobId);

        } catch (Exception e) {
            log.error("Restore execution failed: {}", e.getMessage(), e);
            job.setStatus(RestoreJob.STATUS_FAILED);
            job.setErrorMessage(e.getMessage());
            restoreJobRepository.save(job);
            throw new RuntimeException("Restore failed: " + e.getMessage(), e);
        }
    }

    private void markRestoreJobFailed(UUID jobId, String errorMessage) {
        restoreJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(RestoreJob.STATUS_FAILED);
            job.setErrorMessage(errorMessage);
            restoreJobRepository.save(job);
        });
    }

    /**
     * Wait for PostgreSQL to be ready after restore by polling Patroni.
     *
     * @param node Node to check
     * @param maxWaitSeconds Maximum time to wait
     */
    private void waitForPostgresReady(VpsNode node, int maxWaitSeconds) {
        int pollIntervalMs = 2000;
        int maxAttempts = (maxWaitSeconds * 1000) / pollIntervalMs;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (patroniService.isLeaderNode(node)) {
                log.info("PostgreSQL is ready after {} seconds", (attempt * pollIntervalMs) / 1000);
                return;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for PostgreSQL", e);
            }
        }
        log.warn("PostgreSQL did not become ready within {} seconds, continuing anyway", maxWaitSeconds);
    }

    /**
     * Get restore job
     */
    @Transactional(readOnly = true)
    public RestoreJob getRestoreJob(UUID clusterId, UUID jobId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        return restoreJobRepository.findByIdAndSourceCluster(jobId, cluster)
                .orElseThrow(() -> new IllegalArgumentException("Restore job not found"));
    }

    /**
     * List restore jobs for a cluster
     */
    @Transactional(readOnly = true)
    public List<RestoreJob> listRestoreJobs(UUID clusterId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        return restoreJobRepository.findBySourceClusterOrderByCreatedAtDesc(cluster);
    }

    /**
     * Get backup storage metrics for a cluster
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBackupMetrics(UUID clusterId, User user) {
        Cluster cluster = clusterRepository.findByIdAndUser(clusterId, user)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found"));

        List<Backup> completedBackups = backupRepository.findByClusterAndStatusOrderByCreatedAtDesc(
                cluster, Backup.STATUS_COMPLETED);

        // Calculate total size
        long totalSizeBytes = completedBackups.stream()
                .mapToLong(b -> b.getSizeBytes() != null ? b.getSizeBytes() : 0L)
                .sum();

        // Get oldest and newest backup times
        Instant oldestBackup = completedBackups.isEmpty() ? null :
                completedBackups.get(completedBackups.size() - 1).getCreatedAt();
        Instant newestBackup = completedBackups.isEmpty() ? null :
                completedBackups.get(0).getCreatedAt();

        // Calculate PITR window (earliest to latest recovery time across all backups)
        Instant earliestPitr = completedBackups.stream()
                .map(Backup::getEarliestRecoveryTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        Instant latestPitr = completedBackups.stream()
                .map(Backup::getLatestRecoveryTime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        // Calculate storage trend (daily totals for last 30 days)
        List<Map<String, Object>> storageTrend = calculateStorageTrend(completedBackups, 30);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalSizeBytes", totalSizeBytes);
        metrics.put("formattedTotalSize", FormatUtils.formatBytes(totalSizeBytes));
        metrics.put("backupCount", completedBackups.size());
        metrics.put("oldestBackup", oldestBackup);
        metrics.put("newestBackup", newestBackup);
        metrics.put("earliestPitrTime", earliestPitr);
        metrics.put("latestPitrTime", latestPitr);
        metrics.put("storageTrend", storageTrend);

        return metrics;
    }

    /**
     * Calculate storage trend over the last N days
     */
    private List<Map<String, Object>> calculateStorageTrend(List<Backup> backups, int days) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

            // Sum of all backups that existed on this day (created before day end)
            long sizeOnDay = backups.stream()
                    .filter(b -> b.getCreatedAt().isBefore(dayEnd))
                    .mapToLong(b -> b.getSizeBytes() != null ? b.getSizeBytes() : 0L)
                    .sum();

            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", date.format(formatter));
            dayData.put("sizeBytes", sizeOnDay);
            dayData.put("formattedSize", FormatUtils.formatBytes(sizeOnDay));
            trend.add(dayData);
        }

        return trend;
    }

    // ============ Scheduled Backup Jobs ============

    /**
     * Daily backup at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledDailyBackup() {
        if (!backupEnabled || !s3StorageService.isConfigured()) {
            return;
        }
        log.info("Starting scheduled daily backups...");
        createScheduledBackups(Backup.TYPE_SCHEDULED_DAILY, Backup.RETENTION_DAILY);
    }

    /**
     * Weekly backup at 3 AM on Sundays
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void scheduledWeeklyBackup() {
        if (!backupEnabled || !s3StorageService.isConfigured()) {
            return;
        }
        log.info("Starting scheduled weekly backups...");
        createScheduledBackups(Backup.TYPE_SCHEDULED_WEEKLY, Backup.RETENTION_WEEKLY);
    }

    /**
     * Monthly backup at 4 AM on the 1st
     */
    @Scheduled(cron = "0 0 4 1 * *")
    public void scheduledMonthlyBackup() {
        if (!backupEnabled || !s3StorageService.isConfigured()) {
            return;
        }
        log.info("Starting scheduled monthly backups...");
        createScheduledBackups(Backup.TYPE_SCHEDULED_MONTHLY, Backup.RETENTION_MONTHLY);
    }

    /**
     * Cleanup expired backups at 5 AM daily
     */
    @Scheduled(cron = "0 0 5 * * *")
    public void cleanupExpiredBackups() {
        if (!backupEnabled || !s3StorageService.isConfigured()) {
            return;
        }
        log.info("Starting cleanup of expired backups...");

        List<Backup> expiredBackups = backupRepository.findExpiredBackups(Instant.now());
        for (Backup backup : expiredBackups) {
            try {
                // Delete S3 files
                if (backup.getS3BasePath() != null) {
                    s3StorageService.deleteDirectory(backup.getS3BasePath());
                }
                // Mark as expired
                backup.setStatus(Backup.STATUS_EXPIRED);
                backupRepository.save(backup);
                log.info("Cleaned up expired backup {}", backup.getId());
            } catch (Exception e) {
                log.error("Failed to cleanup backup {}: {}", backup.getId(), e.getMessage());
            }
        }

        log.info("Cleanup completed. Processed {} expired backups.", expiredBackups.size());
    }

    private void createScheduledBackups(String type, String retentionType) {
        List<Cluster> runningClusters = clusterRepository.findByStatus(Cluster.STATUS_RUNNING);

        for (Cluster cluster : runningClusters) {
            try {
                Backup backup = Backup.builder()
                        .cluster(cluster)
                        .type(type)
                        .status(Backup.STATUS_PENDING)
                        .retentionType(retentionType)
                        .build();

                backup = backupRepository.save(backup);
                log.info("Created {} backup {} for cluster {}", type, backup.getId(), cluster.getSlug());

                self.executeBackupAsync(backup.getId());
            } catch (Exception e) {
                log.error("Failed to create scheduled backup for cluster {}: {}",
                        cluster.getSlug(), e.getMessage());
            }
        }
    }
}
