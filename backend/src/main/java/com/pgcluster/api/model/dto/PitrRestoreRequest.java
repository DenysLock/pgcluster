package com.pgcluster.api.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
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
public class PitrRestoreRequest {

    @NotNull(message = "Target time is required for PITR")
    private Instant targetTime;

    @Builder.Default
    private boolean createNewCluster = true;

    @Size(min = 3, max = 50, message = "Cluster name must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Cluster name must start with a letter and contain only lowercase letters, numbers, and hyphens")
    private String newClusterName;

    @Size(min = 1, max = 3, message = "Must specify 1 to 3 node regions")
    private List<String> nodeRegions;

    @Pattern(regexp = "^(cx[2345]3|ccx[123456]3|cpx[123]1|cpx[345]1|cax[123]1)$",
            message = "Invalid node size")
    private String nodeSize;

    @Pattern(regexp = "^(14|15|16|17)$", message = "PostgreSQL version must be 14, 15, 16, or 17")
    private String postgresVersion;

    @AssertTrue(message = "Must specify exactly 1 node (single) or 3 nodes (HA)")
    public boolean isValidNodeCount() {
        return nodeRegions == null || nodeRegions.size() == 1 || nodeRegions.size() == 3;
    }

    @AssertTrue(message = "PITR currently supports only restore to a new cluster")
    public boolean isCreateNewClusterOnly() {
        return createNewCluster;
    }
}
