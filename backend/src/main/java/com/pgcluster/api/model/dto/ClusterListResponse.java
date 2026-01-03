package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ClusterListResponse {

    private List<ClusterResponse> clusters;
    private int count;
}
