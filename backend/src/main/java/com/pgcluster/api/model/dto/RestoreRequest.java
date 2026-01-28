package com.pgcluster.api.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestoreRequest {

    /**
     * Optional target time for Point-in-Time Recovery (PITR).
     * If not provided, a full restore from the backup will be performed.
     * Must be within the backup's recovery time window.
     */
    private Instant targetTime;

    /**
     * If true, create a new cluster instead of in-place restore.
     * Default is true for safety - always creates a new cluster.
     */
    @Builder.Default
    private boolean createNewCluster = true;

    /**
     * Name for the new cluster (required if createNewCluster is true).
     */
    @Size(min = 3, max = 50, message = "Cluster name must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Cluster name must start with a letter and contain only lowercase letters, numbers, and hyphens")
    private String newClusterName;

    /**
     * Node regions for the new cluster.
     * If not provided, uses source cluster's node regions.
     * Must specify exactly 1 or 3 regions if provided.
     */
    @Size(min = 1, max = 3, message = "Must specify 1 to 3 node regions")
    private List<String> nodeRegions;

    /**
     * Custom validation: if nodeRegions provided, must be exactly 1 or 3.
     * 2 nodes is disallowed because etcd quorum would be 2.
     */
    @AssertTrue(message = "Must specify exactly 1 node (single) or 3 nodes (HA)")
    public boolean isValidNodeCount() {
        // Null is allowed (uses source cluster's regions)
        return nodeRegions == null || nodeRegions.size() == 1 || nodeRegions.size() == 3;
    }
}
