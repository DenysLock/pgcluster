package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO for server type information with availability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerTypeDto {
    private String name;        // "cx23", "ccx13", etc.
    private int cores;          // Number of vCPU cores
    private int memory;         // RAM in GB
    private int disk;           // SSD storage in GB
    private Set<String> availableLocations; // Locations where this type is available
}
