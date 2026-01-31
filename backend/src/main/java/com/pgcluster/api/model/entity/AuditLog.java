package com.pgcluster.api.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    // Authentication actions
    public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String AUTH_LOGIN_FAILURE = "AUTH_LOGIN_FAILURE";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";

    // User management actions
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String USER_ENABLED = "USER_ENABLED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";

    // Cluster actions
    public static final String CLUSTER_CREATED = "CLUSTER_CREATED";
    public static final String CLUSTER_DELETED = "CLUSTER_DELETED";
    public static final String CLUSTER_HEALTH_CHECK = "CLUSTER_HEALTH_CHECK";

    // Backup actions
    public static final String BACKUP_INITIATED = "BACKUP_INITIATED";
    public static final String BACKUP_DELETED = "BACKUP_DELETED";
    public static final String BACKUP_RESTORE_INITIATED = "BACKUP_RESTORE_INITIATED";

    // Export actions
    public static final String EXPORT_INITIATED = "EXPORT_INITIATED";
    public static final String EXPORT_DOWNLOADED = "EXPORT_DOWNLOADED";
    public static final String EXPORT_DELETED = "EXPORT_DELETED";

    // Credential access
    public static final String CREDENTIALS_ACCESSED = "CREDENTIALS_ACCESSED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
