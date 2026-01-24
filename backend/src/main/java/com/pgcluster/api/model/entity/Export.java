package com.pgcluster.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Export {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @Column(nullable = false)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    @Builder.Default
    private String format = FORMAT_PG_DUMP;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "s3_path", length = 500)
    private String s3Path;

    @Column(name = "download_url", length = 2000)
    private String downloadUrl;

    @Column(name = "download_expires_at")
    private Instant downloadExpiresAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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

    // Export statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    // Export formats
    public static final String FORMAT_PG_DUMP = "pg_dump";
    public static final String FORMAT_CUSTOM = "custom";
    public static final String FORMAT_DIRECTORY = "directory";
}
