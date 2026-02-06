package com.pgcluster.api.service;

import com.pgcluster.api.model.entity.Cluster;
import com.pgcluster.api.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for updating cluster progress in a separate transaction.
 * Uses REQUIRES_NEW propagation to ensure progress updates are committed immediately,
 * allowing the frontend to see real-time provisioning status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterProgressService {

    private final ClusterRepository clusterRepository;

    /**
     * Update cluster provisioning progress.
     * Commits immediately in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID clusterId, String step, int progress) {
        clusterRepository.findById(clusterId).ifPresent(cluster -> {
            cluster.setProvisioningStep(step);
            cluster.setProvisioningProgress(progress);
            clusterRepository.save(cluster);
            log.info("Cluster {} progress: step={} ({}/{})",
                    cluster.getSlug(), step, progress, Cluster.TOTAL_PROVISIONING_STEPS);
        });
    }

    /**
     * Update cluster status.
     * Commits immediately in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID clusterId, String status, String errorMessage) {
        clusterRepository.findById(clusterId).ifPresent(cluster -> {
            // Don't overwrite DELETING/DELETED status (e.g., from a failed provisioning thread)
            if (Cluster.STATUS_DELETING.equals(cluster.getStatus()) || Cluster.STATUS_DELETED.equals(cluster.getStatus())) {
                log.info("Skipping status update to '{}' for cluster {} — already {}", status, cluster.getSlug(), cluster.getStatus());
                return;
            }
            cluster.setStatus(status);
            if (errorMessage != null) {
                cluster.setErrorMessage(errorMessage);
            }
            clusterRepository.save(cluster);
        });
    }
}
