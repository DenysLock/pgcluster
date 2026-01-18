package com.pgcluster.api.model.entity;

import com.pgcluster.api.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clusters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    @Builder.Default
    private String plan = "dedicated";

    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    // Cluster configuration
    @Column(name = "postgres_version", nullable = false)
    @Builder.Default
    private String postgresVersion = "16";

    @Column(name = "node_count", nullable = false)
    @Builder.Default
    private int nodeCount = 3;

    @Column(name = "node_size", nullable = false)
    @Builder.Default
    private String nodeSize = "cx23";

    @Column(nullable = false)
    @Builder.Default
    private String region = "fsn1";

    // Connection info
    private String hostname;

    @Builder.Default
    private int port = 5432;

    @Column(name = "postgres_password", length = 512)
    @Convert(converter = EncryptedStringConverter.class)
    private String postgresPassword;

    // Resource tracking
    @Column(name = "storage_gb")
    @Builder.Default
    private int storageGb = 40;

    @Column(name = "memory_mb")
    @Builder.Default
    private int memoryMb = 4096;

    @Column(name = "cpu_cores")
    @Builder.Default
    private int cpuCores = 2;

    // Error tracking
    @Column(name = "error_message")
    private String errorMessage;

    // Provisioning progress tracking
    @Column(name = "provisioning_step")
    private String provisioningStep;

    @Column(name = "provisioning_progress")
    private Integer provisioningProgress;

    // Relationships
    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VpsNode> nodes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Cluster statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CREATING = "creating";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_DELETING = "deleting";
    public static final String STATUS_DELETED = "deleted";

    // Provisioning steps (1-6)
    public static final String STEP_CREATING_SERVERS = "creating_servers";
    public static final String STEP_WAITING_SSH = "waiting_ssh";
    public static final String STEP_BUILDING_CONFIG = "building_config";
    public static final String STEP_STARTING_CONTAINERS = "starting_containers";
    public static final String STEP_ELECTING_LEADER = "electing_leader";
    public static final String STEP_CREATING_DNS = "creating_dns";
    public static final int TOTAL_PROVISIONING_STEPS = 6;
}
