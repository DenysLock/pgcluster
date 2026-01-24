package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BackupRepository extends JpaRepository<Backup, UUID> {

    List<Backup> findByClusterOrderByCreatedAtDesc(Cluster cluster);

    List<Backup> findByClusterAndStatusOrderByCreatedAtDesc(Cluster cluster, String status);

    Optional<Backup> findByIdAndCluster(UUID id, Cluster cluster);

    @Query("SELECT b FROM Backup b WHERE b.cluster = :cluster AND b.status = :status ORDER BY b.createdAt DESC")
    List<Backup> findCompletedBackups(@Param("cluster") Cluster cluster, @Param("status") String status);

    @Query("SELECT b FROM Backup b WHERE b.status = 'completed' AND b.expiresAt < :now")
    List<Backup> findExpiredBackups(@Param("now") Instant now);

    @Query("SELECT b FROM Backup b WHERE b.cluster.id = :clusterId AND b.status = 'completed' ORDER BY b.createdAt DESC")
    List<Backup> findLatestCompletedBackup(@Param("clusterId") UUID clusterId);

    long countByClusterAndStatus(Cluster cluster, String status);

    @Query("SELECT COALESCE(SUM(b.sizeBytes), 0) FROM Backup b WHERE b.cluster = :cluster AND b.status = 'completed'")
    long sumSizeByCluster(@Param("cluster") Cluster cluster);

    List<Backup> findByClusterAndTypeAndStatusOrderByCreatedAtDesc(Cluster cluster, String type, String status);

    @Query("SELECT b FROM Backup b WHERE b.cluster.status = 'running' AND b.cluster.id IN " +
           "(SELECT DISTINCT c.id FROM Cluster c WHERE c.status = 'running')")
    List<Backup> findBackupsForRunningClusters();

    @Query("SELECT DISTINCT b.cluster FROM Backup b WHERE b.status = 'completed'")
    List<Cluster> findClustersWithBackups();
}
