package com.pgcluster.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "s3_base_path", length = 500)
    private String s3BasePath;

    @Column(name = "s3_wal_path", length = 500)
    private String s3WalPath;

    @Column(name = "wal_start_lsn", length = 50)
    private String walStartLsn;

    @Column(name = "wal_end_lsn", length = 50)
    private String walEndLsn;

    @Column(name = "earliest_recovery_time")
    private Instant earliestRecoveryTime;

    @Column(name = "latest_recovery_time")
    private Instant latestRecoveryTime;

    @Column(name = "retention_type", length = 50)
    private String retentionType;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "pgbackrest_label", length = 50)
    private String pgbackrestLabel;

    @Column(name = "backup_type", length = 20)
    private String backupType;

    @Column(name = "requested_backup_type", length = 20)
    private String requestedBackupType;

    @Column(name = "current_step", length = 50)
    private String currentStep;

    @Column(name = "progress_percent")
    @Builder.Default
    private Integer progressPercent = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Backup statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_DELETED = "deleted";

    // Backup types
    public static final String TYPE_MANUAL = "manual";
    public static final String TYPE_SCHEDULED_DAILY = "scheduled_daily";
    public static final String TYPE_SCHEDULED_WEEKLY = "scheduled_weekly";
    public static final String TYPE_SCHEDULED_MONTHLY = "scheduled_monthly";

    // Retention types
    public static final String RETENTION_DAILY = "daily";
    public static final String RETENTION_WEEKLY = "weekly";
    public static final String RETENTION_MONTHLY = "monthly";
    public static final String RETENTION_MANUAL = "manual";

    // pgBackRest backup types (physical backup method)
    public static final String BACKUP_TYPE_FULL = "full";
    public static final String BACKUP_TYPE_DIFF = "diff";
    public static final String BACKUP_TYPE_INCR = "incr";

    // Backup progress steps
    public static final String STEP_PENDING = "pending";
    public static final String STEP_PREPARING = "preparing";
    public static final String STEP_BACKING_UP = "backing_up";
    public static final String STEP_UPLOADING = "uploading";
    public static final String STEP_VERIFYING = "verifying";
    public static final String STEP_COMPLETED = "completed";
    public static final String STEP_FAILED = "failed";
}
