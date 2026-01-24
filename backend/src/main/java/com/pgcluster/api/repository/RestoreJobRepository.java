package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.RestoreJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestoreJobRepository extends JpaRepository<RestoreJob, UUID> {

    List<RestoreJob> findBySourceClusterOrderByCreatedAtDesc(Cluster sourceCluster);

    List<RestoreJob> findByTargetClusterOrderByCreatedAtDesc(Cluster targetCluster);

    List<RestoreJob> findByBackupOrderByCreatedAtDesc(Backup backup);

    Optional<RestoreJob> findByIdAndSourceCluster(UUID id, Cluster sourceCluster);

    @Query("SELECT r FROM RestoreJob r WHERE r.status IN ('pending', 'in_progress')")
    List<RestoreJob> findActiveRestoreJobs();

    @Query("SELECT r FROM RestoreJob r WHERE r.sourceCluster = :cluster OR r.targetCluster = :cluster ORDER BY r.createdAt DESC")
    List<RestoreJob> findByCluster(@Param("cluster") Cluster cluster);

    long countBySourceClusterAndStatus(Cluster cluster, String status);

    @Query("SELECT r FROM RestoreJob r WHERE r.sourceCluster.id = :clusterId AND r.status IN ('pending', 'in_progress')")
    List<RestoreJob> findPendingJobsForCluster(@Param("clusterId") UUID clusterId);
}
