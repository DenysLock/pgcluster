package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO containing available server types grouped by category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerTypesResponse {
    private List<ServerTypeDto> shared;    // cx family (shared vCPU)
    private List<ServerTypeDto> dedicated; // ccx family (dedicated vCPU)
}
