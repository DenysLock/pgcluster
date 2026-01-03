package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AdminClusterListResponse {

    private List<AdminClusterResponse> clusters;
    private int count;
}
