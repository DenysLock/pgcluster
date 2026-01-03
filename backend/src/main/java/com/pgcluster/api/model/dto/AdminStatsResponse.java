package com.pgcluster.api.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminStatsResponse {

    @JsonProperty("total_clusters")
    private long totalClusters;

    @JsonProperty("running_clusters")
    private long runningClusters;

    @JsonProperty("total_users")
    private long totalUsers;
}
