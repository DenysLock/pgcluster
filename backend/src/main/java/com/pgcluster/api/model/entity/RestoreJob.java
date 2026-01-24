package com.pgcluster.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restore_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_cluster_id", nullable = false)
    private Cluster sourceCluster;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_cluster_id")
    private Cluster targetCluster;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "backup_id", nullable = false)
    private Backup backup;

    @Column(name = "restore_type", nullable = false)
    private String restoreType;

    @Column(name = "target_time")
    private Instant targetTime;

    @Column(nullable = false)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Column
    @Builder.Default
    private Integer progress = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Restore job statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    // Restore types
    public static final String TYPE_FULL = "full";
    public static final String TYPE_PITR = "pitr";

    // Restore steps
    public static final String STEP_CREATING_CLUSTER = "creating_cluster";
    public static final String STEP_DOWNLOADING_BACKUP = "downloading_backup";
    public static final String STEP_EXTRACTING_BACKUP = "extracting_backup";
    public static final String STEP_DOWNLOADING_WAL = "downloading_wal";
    public static final String STEP_CONFIGURING_RECOVERY = "configuring_recovery";
    public static final String STEP_STARTING_POSTGRES = "starting_postgres";
    public static final String STEP_VERIFYING = "verifying";
}
