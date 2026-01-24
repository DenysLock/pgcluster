package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.Export;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExportRepository extends JpaRepository<Export, UUID> {

    List<Export> findByClusterOrderByCreatedAtDesc(Cluster cluster);

    List<Export> findByClusterAndStatusOrderByCreatedAtDesc(Cluster cluster, String status);

    Optional<Export> findByIdAndCluster(UUID id, Cluster cluster);

    long countByClusterAndStatus(Cluster cluster, String status);
}
