package com.pgcluster.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vps_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VpsNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    // Hetzner server info
    @Column(name = "hetzner_id")
    private Long hetznerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "public_ip")
    private String publicIp;

    @Column(name = "private_ip")
    private String privateIp;

    // Node configuration
    @Column(name = "server_type", nullable = false)
    @Builder.Default
    private String serverType = "cx23";

    @Column(nullable = false)
    @Builder.Default
    private String location = "fsn1";

    @Column(nullable = false)
    @Builder.Default
    private String status = "creating";

    @Column(nullable = false)
    @Builder.Default
    private String role = "replica"; // leader, replica

    // Error tracking
    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Node statuses
    public static final String STATUS_CREATING = "creating";
    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_DELETING = "deleting";
}
