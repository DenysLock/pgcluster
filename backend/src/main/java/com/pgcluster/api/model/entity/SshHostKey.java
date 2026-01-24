package com.pgcluster.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a trusted SSH host key fingerprint.
 * Used for Trust-On-First-Use (TOFU) verification of SSH connections.
 */
@Entity
@Table(name = "ssh_host_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SshHostKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String host;

    @Column(nullable = false)
    private String fingerprint;

    @Column(name = "key_type")
    private String keyType;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastVerifiedAt == null) {
            lastVerifiedAt = now;
        }
    }
}
