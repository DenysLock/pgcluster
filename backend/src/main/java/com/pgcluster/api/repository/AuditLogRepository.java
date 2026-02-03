package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    @Query(value = "SELECT * FROM audit_logs a WHERE " +
           "(CAST(:userId AS UUID) IS NULL OR a.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:clusterId AS VARCHAR) IS NULL OR " +
           "  (a.details IS NOT NULL AND a.details->>'cluster_id' = CAST(:clusterId AS VARCHAR)) OR " +
           "  (a.resource_type = 'cluster' AND CAST(a.resource_id AS VARCHAR) = CAST(:clusterId AS VARCHAR))) AND " +
           "(CAST(:action AS VARCHAR) IS NULL OR a.action = CAST(:action AS VARCHAR)) AND " +
           "(CAST(:resourceType AS VARCHAR) IS NULL OR a.resource_type = CAST(:resourceType AS VARCHAR)) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR a.timestamp >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR a.timestamp <= CAST(:endDate AS TIMESTAMP)) " +
           "ORDER BY a.timestamp DESC",
           countQuery = "SELECT COUNT(*) FROM audit_logs a WHERE " +
           "(CAST(:userId AS UUID) IS NULL OR a.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:clusterId AS VARCHAR) IS NULL OR " +
           "  (a.details IS NOT NULL AND a.details->>'cluster_id' = CAST(:clusterId AS VARCHAR)) OR " +
           "  (a.resource_type = 'cluster' AND CAST(a.resource_id AS VARCHAR) = CAST(:clusterId AS VARCHAR))) AND " +
           "(CAST(:action AS VARCHAR) IS NULL OR a.action = CAST(:action AS VARCHAR)) AND " +
           "(CAST(:resourceType AS VARCHAR) IS NULL OR a.resource_type = CAST(:resourceType AS VARCHAR)) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR a.timestamp >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR a.timestamp <= CAST(:endDate AS TIMESTAMP))",
           nativeQuery = true)
    Page<AuditLog> findWithFilters(
            @Param("userId") UUID userId,
            @Param("clusterId") String clusterId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    @Query(value = "SELECT * FROM audit_logs a WHERE " +
           "(CAST(:userId AS UUID) IS NULL OR a.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:clusterId AS VARCHAR) IS NULL OR " +
           "  (a.details IS NOT NULL AND a.details->>'cluster_id' = CAST(:clusterId AS VARCHAR)) OR " +
           "  (a.resource_type = 'cluster' AND CAST(a.resource_id AS VARCHAR) = CAST(:clusterId AS VARCHAR))) AND " +
           "(CAST(:action AS VARCHAR) IS NULL OR a.action = CAST(:action AS VARCHAR)) AND " +
           "(CAST(:resourceType AS VARCHAR) IS NULL OR a.resource_type = CAST(:resourceType AS VARCHAR)) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR a.timestamp >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR a.timestamp <= CAST(:endDate AS TIMESTAMP)) " +
           "ORDER BY a.timestamp DESC LIMIT 50000",
           nativeQuery = true)
    List<AuditLog> findWithFiltersForExport(
            @Param("userId") UUID userId,
            @Param("clusterId") String clusterId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    List<AuditLog> findTop50ByUserIdOrderByTimestampDesc(UUID userId);

    long countByAction(String action);

    long countByUserId(UUID userId);
}
