package com.pgcluster.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcluster.api.model.dto.PgBackRestBackupInfo;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing pgBackRest backup operations.
 * pgBackRest is an enterprise-grade backup tool supporting:
 * - Full, differential, and incremental backups
 * - Native S3 integration
 * - Integrated WAL archiving for true PITR
 * - Parallel backup/restore operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgBackRestService {

    private final SshService sshService;
    private final S3StorageService s3StorageService;
    private final ObjectMapper objectMapper;

    @Value("${s3.endpoint:}")
    private String s3Endpoint;

    @Value("${s3.access-key:}")
    private String s3AccessKey;

    @Value("${s3.secret-key:}")
    private String s3SecretKey;

    @Value("${s3.bucket:pgcluster-backups}")
    private String s3Bucket;

    @Value("${s3.region:eu-central-1}")
    private String s3Region;

    @Value("${backup.retention.full:2}")
    private int retentionFull;

    @Value("${backup.retention.diff:7}")
    private int retentionDiff;

    @Value("${timeouts.backup:3600000}")
    private int backupTimeoutMs;

    @Value("${timeouts.restore:7200000}")
    private int restoreTimeoutMs;

    @Value("${timeouts.ssh-command:60000}")
    private int commandTimeoutMs;

    // pgBackRest paths (configurable)
    @Value("${pgbackrest.config-path:/etc/pgbackrest/pgbackrest.conf}")
    private String pgbackrestConfigPath;

    @Value("${pgbackrest.config-dir:/etc/pgbackrest}")
    private String pgbackrestConfigDir;

    @Value("${pgbackrest.log-path:/var/log/pgbackrest}")
    private String pgbackrestLogPath;

    @Value("${pgbackrest.spool-path:/var/spool/pgbackrest}")
    private String pgbackrestSpoolPath;

    // PostgreSQL paths (configurable)
    @Value("${postgres.data-path:/var/lib/postgresql/data}")
    private String postgresDataPath;

    @Value("${postgres.socket-path:/var/run/postgresql}")
    private String postgresSocketPath;

    /**
     * Generate pgbackrest.conf configuration for a cluster.
     *
     * @param cluster The cluster to generate config for
     * @return The configuration file content
     */
    public String generateConfig(Cluster cluster) {
        String stanzaName = cluster.getSlug();

        return """
            [global]
            repo1-type=s3
            repo1-s3-endpoint=%s
            repo1-s3-bucket=%s
            repo1-s3-region=%s
            repo1-s3-key=%s
            repo1-s3-key-secret=%s
            repo1-path=/pgbackrest/%s
            repo1-retention-full=%d
            repo1-retention-diff=%d
            repo1-s3-uri-style=path
            process-max=2
            compress-type=lz4
            archive-async=y
            spool-path=%s
            log-path=%s

            [%s]
            pg1-path=%s
            pg1-port=5432
            pg1-socket-path=%s
            """.formatted(
                s3Endpoint.replace("https://", "").replace("http://", ""),
                s3Bucket,
                s3Region,
                s3AccessKey,
                s3SecretKey,
                cluster.getId(),
                retentionFull,
                retentionDiff,
                pgbackrestSpoolPath,
                pgbackrestLogPath,
                stanzaName,
                postgresDataPath,
                postgresSocketPath
        );
    }

    /**
     * Upload pgBackRest configuration to a node.
     *
     * @param cluster The cluster
     * @param node The node to upload to
     */
    public void uploadConfig(Cluster cluster, VpsNode node) {
        log.info("Uploading pgBackRest config to node {}", node.getName());

        String config = generateConfig(cluster);

        // Ensure config directory exists with correct permissions
        // chown to 999:999 (postgres user in container)
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("mkdir -p %s && chown 999:999 %s && chmod 750 %s",
                        pgbackrestConfigDir, pgbackrestConfigDir, pgbackrestConfigDir),
                commandTimeoutMs
        );

        // Upload configuration
        sshService.uploadContent(node.getPublicIp(), config, pgbackrestConfigPath);

        // Set ownership and permissions (readable by postgres user UID 999)
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("chown 999:999 %s && chmod 640 %s",
                        pgbackrestConfigPath, pgbackrestConfigPath),
                commandTimeoutMs
        );

        // Also fix log/spool directory permissions
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("chown -R 999:999 %s %s 2>/dev/null || true",
                        pgbackrestLogPath, pgbackrestSpoolPath),
                commandTimeoutMs
        );

        log.info("pgBackRest config uploaded to node {}", node.getName());
    }

    /**
     * Create/initialize the pgBackRest stanza for a cluster.
     * Must be called after PostgreSQL is running.
     *
     * @param cluster The cluster
     * @param node The leader node
     */
    public void createStanza(Cluster cluster, VpsNode node) {
        String stanzaName = cluster.getSlug();
        log.info("Creating pgBackRest stanza '{}' on node {}", stanzaName, node.getName());

        // Run stanza-create inside the patroni container where postgres runs
        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s stanza-create",
                stanzaName
        );

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                commandTimeoutMs
        );

        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to create pgBackRest stanza: " + result.getStderr());
        }

        // Verify stanza with check command
        String checkCommand = String.format(
                "docker exec patroni pgbackrest --stanza=%s check",
                stanzaName
        );

        SshService.CommandResult checkResult = sshService.executeCommand(
                node.getPublicIp(),
                checkCommand,
                commandTimeoutMs
        );

        if (!checkResult.isSuccess()) {
            log.warn("pgBackRest stanza check warning: {}", checkResult.getStderr());
        }

        log.info("pgBackRest stanza '{}' created successfully", stanzaName);
    }

    /**
     * Execute a full backup.
     *
     * @param cluster The cluster
     * @param node The leader node
     * @return Backup information
     */
    public PgBackRestBackupInfo executeFullBackup(Cluster cluster, VpsNode node) {
        return executeBackup(cluster, node, PgBackRestBackupInfo.TYPE_FULL);
    }

    /**
     * Execute a differential backup (changes since last full backup).
     *
     * @param cluster The cluster
     * @param node The leader node
     * @return Backup information
     */
    public PgBackRestBackupInfo executeDifferentialBackup(Cluster cluster, VpsNode node) {
        return executeBackup(cluster, node, PgBackRestBackupInfo.TYPE_DIFF);
    }

    /**
     * Execute an incremental backup (changes since any last backup).
     *
     * @param cluster The cluster
     * @param node The leader node
     * @return Backup information
     */
    public PgBackRestBackupInfo executeIncrementalBackup(Cluster cluster, VpsNode node) {
        return executeBackup(cluster, node, PgBackRestBackupInfo.TYPE_INCR);
    }

    /**
     * Execute a backup of the specified type.
     */
    private PgBackRestBackupInfo executeBackup(Cluster cluster, VpsNode node, String backupType) {
        String stanzaName = cluster.getSlug();
        log.info("Starting {} backup for cluster {} on node {}", backupType, stanzaName, node.getName());

        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s --type=%s backup",
                stanzaName, backupType
        );

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                backupTimeoutMs
        );

        if (!result.isSuccess()) {
            // pgbackrest outputs errors to stdout, not stderr
            String errorMsg = result.getStderr();
            if (errorMsg == null || errorMsg.isBlank()) {
                errorMsg = result.getStdout();
            }
            log.error("pgBackRest backup failed. Exit code: {}, stdout: {}, stderr: {}",
                    result.getExitCode(), result.getStdout(), result.getStderr());
            throw new RuntimeException("Backup failed: " + errorMsg);
        }

        log.info("{} backup completed for cluster {}", backupType, stanzaName);

        // Get info about the latest backup (optimized: shorter timeout for info command)
        PgBackRestBackupInfo latestBackup = getLatestBackupInfo(cluster, node);
        if (latestBackup == null) {
            throw new RuntimeException("Backup completed but no backup info found");
        }

        return latestBackup;
    }

    /**
     * Get information about the most recent backup.
     * Optimized for speed with a shorter timeout since info commands are fast.
     *
     * @param cluster The cluster
     * @param node The node to query
     * @return The latest backup info, or null if no backups exist
     */
    public PgBackRestBackupInfo getLatestBackupInfo(Cluster cluster, VpsNode node) {
        String stanzaName = cluster.getSlug();
        log.debug("Getting latest backup info for cluster {}", stanzaName);

        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s info --output=json",
                stanzaName
        );

        // Use shorter timeout for info command (should complete in seconds)
        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                15000  // 15 seconds instead of 60
        );

        if (!result.isSuccess()) {
            log.warn("Failed to get backup info: {}", result.getStderr());
            return null;
        }

        List<PgBackRestBackupInfo> backups = parseBackupInfoJson(result.getStdout());
        return backups.isEmpty() ? null : backups.get(backups.size() - 1);
    }

    /**
     * List all backups for a cluster.
     *
     * @param cluster The cluster
     * @param node The node to query
     * @return List of backup information
     */
    public List<PgBackRestBackupInfo> listBackups(Cluster cluster, VpsNode node) {
        String stanzaName = cluster.getSlug();
        log.debug("Listing backups for cluster {}", stanzaName);

        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s info --output=json",
                stanzaName
        );

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                commandTimeoutMs
        );

        if (!result.isSuccess()) {
            log.warn("Failed to list backups: {}", result.getStderr());
            return new ArrayList<>();
        }

        return parseBackupInfoJson(result.getStdout());
    }

    /**
     * Parse pgBackRest info JSON output into backup info objects.
     */
    private List<PgBackRestBackupInfo> parseBackupInfoJson(String json) {
        List<PgBackRestBackupInfo> backups = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);

            // pgBackRest info returns an array of stanzas
            if (root.isArray() && root.size() > 0) {
                JsonNode stanza = root.get(0);
                JsonNode backupArray = stanza.get("backup");

                if (backupArray != null && backupArray.isArray()) {
                    for (JsonNode backupNode : backupArray) {
                        PgBackRestBackupInfo info = PgBackRestBackupInfo.builder()
                                .label(getJsonString(backupNode, "label"))
                                .type(getJsonString(backupNode, "type"))
                                .build();

                        // Parse database info
                        JsonNode dbInfo = backupNode.get("database");
                        if (dbInfo != null) {
                            info.setDatabaseSizeBytes(getJsonLong(dbInfo, "repo-size"));
                        }

                        // Parse archive info for WAL positions
                        JsonNode archiveNode = backupNode.get("archive");
                        if (archiveNode != null) {
                            info.setWalStartLsn(getJsonString(archiveNode, "start"));
                            info.setWalStopLsn(getJsonString(archiveNode, "stop"));
                        }

                        // Parse timestamps
                        JsonNode timestampNode = backupNode.get("timestamp");
                        if (timestampNode != null) {
                            Long startEpoch = getJsonLong(timestampNode, "start");
                            Long stopEpoch = getJsonLong(timestampNode, "stop");
                            if (startEpoch != null) {
                                info.setStartTime(Instant.ofEpochSecond(startEpoch));
                            }
                            if (stopEpoch != null) {
                                info.setStopTime(Instant.ofEpochSecond(stopEpoch));
                            }
                        }

                        // Parse info section for sizes
                        JsonNode infoNode = backupNode.get("info");
                        if (infoNode != null) {
                            JsonNode repoNode = infoNode.get("repository");
                            if (repoNode != null) {
                                info.setBackupSizeBytes(getJsonLong(repoNode, "size"));
                            }
                        }

                        // Parse prior backup reference
                        info.setPriorLabel(getJsonString(backupNode, "prior"));

                        backups.add(info);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse pgBackRest info JSON: {}", e.getMessage());
        }

        return backups;
    }

    private String getJsonString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private Long getJsonLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asLong() : null;
    }

    /**
     * Execute a restore from backup.
     *
     * @param cluster The cluster
     * @param node The node to restore to
     * @param backupLabel Optional specific backup label (null for latest)
     * @param targetTime Optional PITR target time (null for latest)
     */
    public void executeRestore(Cluster cluster, VpsNode node, String backupLabel, Instant targetTime) {
        String stanzaName = cluster.getSlug();
        log.info("Starting restore for cluster {} on node {}", stanzaName, node.getName());

        // Stop PostgreSQL before restore
        log.info("Stopping PostgreSQL for restore...");
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("docker exec patroni pg_ctl stop -D %s -m fast", postgresDataPath),
                commandTimeoutMs
        );

        // Build restore command
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append(String.format(
                "docker exec patroni pgbackrest --stanza=%s --delta",
                stanzaName
        ));

        // Add specific backup set if provided
        if (backupLabel != null && !backupLabel.isEmpty()) {
            commandBuilder.append(String.format(" --set=%s", backupLabel));
        }

        // Add PITR target time if provided
        if (targetTime != null) {
            commandBuilder.append(String.format(
                    " --type=time --target=\"%s\"",
                    targetTime.toString().replace("T", " ").replace("Z", "")
            ));
        }

        commandBuilder.append(" restore");

        String command = commandBuilder.toString();
        log.info("Executing restore command: {}", command);

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                restoreTimeoutMs
        );

        if (!result.isSuccess()) {
            throw new RuntimeException("Restore failed: " + result.getStderr());
        }

        // Start PostgreSQL after restore
        log.info("Starting PostgreSQL after restore...");
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("docker exec patroni pg_ctl start -D %s", postgresDataPath),
                commandTimeoutMs
        );

        log.info("Restore completed for cluster {}", stanzaName);
    }

    /**
     * Run retention policy cleanup (expire old backups).
     *
     * @param cluster The cluster
     * @param node The node
     */
    public void expireBackups(Cluster cluster, VpsNode node) {
        String stanzaName = cluster.getSlug();
        log.info("Running backup expiration for cluster {}", stanzaName);

        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s expire",
                stanzaName
        );

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                commandTimeoutMs
        );

        if (!result.isSuccess()) {
            log.warn("Backup expiration warning: {}", result.getStderr());
        } else {
            log.info("Backup expiration completed for cluster {}", stanzaName);
        }
    }

    /**
     * Delete a specific backup using pgBackRest expire with --set option.
     * This properly handles chain dependencies and updates backup.info.
     *
     * @param cluster The cluster
     * @param node The leader node
     * @param backupLabel The pgBackRest backup label to delete (e.g., "20260123-193909F")
     */
    public void expireSpecificBackup(Cluster cluster, VpsNode node, String backupLabel) {
        String stanzaName = cluster.getSlug();
        log.info("Deleting specific backup {} for cluster {}", backupLabel, stanzaName);

        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s expire --set=%s",
                stanzaName, backupLabel
        );

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                commandTimeoutMs
        );

        if (!result.isSuccess()) {
            String errorMsg = result.getStderr();
            if (errorMsg == null || errorMsg.isBlank()) {
                errorMsg = result.getStdout();
            }
            log.error("Failed to delete backup {}. Exit code: {}, stdout: {}, stderr: {}",
                    backupLabel, result.getExitCode(), result.getStdout(), result.getStderr());
            throw new RuntimeException("Failed to delete backup: " + errorMsg);
        }

        log.info("Backup {} deleted successfully for cluster {}", backupLabel, stanzaName);
    }

    /**
     * Check if pgBackRest is properly configured and working.
     *
     * @param cluster The cluster
     * @param node The node to check
     * @return true if pgBackRest is operational
     */
    public boolean checkStanza(Cluster cluster, VpsNode node) {
        String stanzaName = cluster.getSlug();

        String command = String.format(
                "docker exec patroni pgbackrest --stanza=%s check",
                stanzaName
        );

        SshService.CommandResult result = sshService.executeCommand(
                node.getPublicIp(),
                command,
                commandTimeoutMs
        );

        return result.isSuccess();
    }

    /**
     * Check if a full backup exists for the cluster.
     *
     * @param cluster The cluster
     * @param node The node to query
     * @return true if at least one full backup exists
     */
    public boolean hasFullBackup(Cluster cluster, VpsNode node) {
        List<PgBackRestBackupInfo> backups = listBackups(cluster, node);
        return backups.stream()
                .anyMatch(b -> PgBackRestBackupInfo.TYPE_FULL.equals(b.getType()));
    }

    /**
     * Generate pgBackRest config that reads from SOURCE cluster's repository.
     * Used during restore to a NEW cluster.
     *
     * IMPORTANT: This config is READ-ONLY for the source backup.
     * After restore completes, we reconfigure to point to the new cluster's repo.
     *
     * @param sourceCluster The source cluster whose backup we're reading from
     * @return The configuration file content
     */
    public String generateRestoreConfig(Cluster sourceCluster) {
        String stanzaName = sourceCluster.getSlug();  // Use SOURCE cluster's stanza

        return """
            [global]
            repo1-type=s3
            repo1-s3-endpoint=%s
            repo1-s3-bucket=%s
            repo1-s3-region=%s
            repo1-s3-key=%s
            repo1-s3-key-secret=%s
            repo1-path=/pgbackrest/%s
            repo1-s3-uri-style=path
            process-max=2
            compress-type=lz4
            log-path=%s

            [%s]
            pg1-path=%s
            pg1-port=5432
            pg1-socket-path=%s
            """.formatted(
                s3Endpoint.replace("https://", "").replace("http://", ""),
                s3Bucket,
                s3Region,
                s3AccessKey,
                s3SecretKey,
                sourceCluster.getId(),  // Point to SOURCE cluster's backup path
                pgbackrestLogPath,
                stanzaName,             // SOURCE cluster's stanza name
                postgresDataPath,
                postgresSocketPath
        );
    }

    /**
     * Upload restore-specific pgBackRest config (pointing to source cluster).
     *
     * @param sourceCluster The source cluster whose backup we're reading from
     * @param node The node to upload to
     */
    public void uploadRestoreConfig(Cluster sourceCluster, VpsNode node) {
        log.info("Uploading pgBackRest restore config to node {} (source: {})",
                node.getName(), sourceCluster.getSlug());

        String config = generateRestoreConfig(sourceCluster);

        // Ensure config directory exists with correct permissions
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("mkdir -p %s && chown 999:999 %s && chmod 750 %s",
                        pgbackrestConfigDir, pgbackrestConfigDir, pgbackrestConfigDir),
                commandTimeoutMs
        );

        // Upload configuration
        sshService.uploadContent(node.getPublicIp(), config, pgbackrestConfigPath);

        // Set ownership and permissions
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("chown 999:999 %s && chmod 640 %s",
                        pgbackrestConfigPath, pgbackrestConfigPath),
                commandTimeoutMs
        );

        // Fix log/spool directory permissions
        sshService.executeCommand(
                node.getPublicIp(),
                String.format("mkdir -p %s %s && chown -R 999:999 %s %s 2>/dev/null || true",
                        pgbackrestLogPath, pgbackrestSpoolPath, pgbackrestLogPath, pgbackrestSpoolPath),
                commandTimeoutMs
        );

        log.info("pgBackRest restore config uploaded to node {}", node.getName());
    }
}
