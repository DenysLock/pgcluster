package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClusterRepository extends JpaRepository<Cluster, UUID> {

    List<Cluster> findByUserOrderByCreatedAtDesc(User user);

    Optional<Cluster> findByIdAndUser(UUID id, User user);

    Optional<Cluster> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT c FROM Cluster c LEFT JOIN FETCH c.nodes WHERE c.id = :id AND c.user = :user")
    Optional<Cluster> findByIdAndUserWithNodes(@Param("id") UUID id, @Param("user") User user);

    long countByUser(User user);

    List<Cluster> findByStatus(String status);

    @Query("SELECT DISTINCT c FROM Cluster c LEFT JOIN FETCH c.nodes WHERE c.status = :status")
    List<Cluster> findByStatusWithNodes(@Param("status") String status);

    // Admin queries
    long countByStatus(String status);

    long countByStatusNot(String status);

    @Query("SELECT c FROM Cluster c LEFT JOIN FETCH c.user WHERE c.status != 'deleted' ORDER BY c.createdAt DESC")
    List<Cluster> findAllWithUser();

    @Query("SELECT c FROM Cluster c LEFT JOIN FETCH c.user LEFT JOIN FETCH c.nodes WHERE c.id = :id")
    Optional<Cluster> findByIdWithUserAndNodes(@Param("id") UUID id);
}
