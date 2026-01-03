package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.VpsNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VpsNodeRepository extends JpaRepository<VpsNode, UUID> {

    List<VpsNode> findByCluster(Cluster cluster);

    List<VpsNode> findByClusterOrderByCreatedAt(Cluster cluster);

    Optional<VpsNode> findByHetznerId(Long hetznerId);

    List<VpsNode> findByStatus(String status);

    Optional<VpsNode> findByClusterAndRole(Cluster cluster, String role);
}
