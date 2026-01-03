package com.pgcluster.api.service;

import com.pgcluster.api.exception.ApiException;
import com.pgcluster.api.model.dto.AdminClusterListResponse;
import com.pgcluster.api.model.dto.AdminClusterResponse;
import com.pgcluster.api.model.dto.AdminStatsResponse;
import com.pgcluster.api.model.dto.AdminUserListResponse;
import com.pgcluster.api.model.dto.AdminUserResponse;
import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.repository.ClusterRepository;
import com.pgcluster.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;

    /**
     * Get platform-wide statistics (excludes deleted clusters)
     */
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalClusters = clusterRepository.countByStatusNot(Cluster.STATUS_DELETED);
        long runningClusters = clusterRepository.countByStatus(Cluster.STATUS_RUNNING);
        long totalUsers = userRepository.count();

        return AdminStatsResponse.builder()
                .totalClusters(totalClusters)
                .runningClusters(runningClusters)
                .totalUsers(totalUsers)
                .build();
    }

    /**
     * List all clusters across all users
     */
    @Transactional(readOnly = true)
    public AdminClusterListResponse listAllClusters() {
        List<Cluster> clusters = clusterRepository.findAllWithUser();

        List<AdminClusterResponse> responses = clusters.stream()
                .map(c -> AdminClusterResponse.fromEntity(c, false))
                .toList();

        return AdminClusterListResponse.builder()
                .clusters(responses)
                .count(responses.size())
                .build();
    }

    /**
     * Get specific cluster details (any user's cluster)
     */
    @Transactional(readOnly = true)
    public AdminClusterResponse getCluster(UUID id) {
        Cluster cluster = clusterRepository.findByIdWithUserAndNodes(id)
                .orElseThrow(() -> new ApiException("Cluster not found", HttpStatus.NOT_FOUND));

        return AdminClusterResponse.fromEntity(cluster, true);
    }

    /**
     * List all users
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse listAllUsers() {
        List<AdminUserResponse> users = userRepository.findAll().stream()
                .map(AdminUserResponse::fromEntity)
                .toList();

        return AdminUserListResponse.builder()
                .users(users)
                .count(users.size())
                .build();
    }
}
