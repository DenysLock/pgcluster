package com.pgcluster.api.controller;

import com.pgcluster.api.model.dto.ClusterCreateRequest;
import com.pgcluster.api.model.dto.ClusterCredentialsResponse;
import com.pgcluster.api.model.dto.ClusterHealthResponse;
import com.pgcluster.api.model.dto.ClusterListResponse;
import com.pgcluster.api.model.dto.ClusterMetricsResponse;
import com.pgcluster.api.model.dto.ClusterResponse;
import com.pgcluster.api.model.dto.LocationDto;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.service.ClusterService;
import com.pgcluster.api.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clusters")
@RequiredArgsConstructor
@Tag(name = "Clusters", description = "PostgreSQL cluster management")
@SecurityRequirement(name = "bearerAuth")
public class ClusterController {

    private final ClusterService clusterService;
    private final MetricsService metricsService;

    @GetMapping
    @Operation(summary = "List all clusters for current user")
    public ResponseEntity<ClusterListResponse> listClusters(@AuthenticationPrincipal User user) {
        ClusterListResponse response = clusterService.listClusters(user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/locations")
    @Operation(summary = "Get available Hetzner locations for node placement")
    public ResponseEntity<List<LocationDto>> getLocations() {
        List<LocationDto> locations = clusterService.getAvailableLocations();
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cluster details")
    public ResponseEntity<ClusterResponse> getCluster(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        ClusterResponse response = clusterService.getCluster(id, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create a new cluster")
    public ResponseEntity<ClusterResponse> createCluster(
            @Valid @RequestBody ClusterCreateRequest request,
            @AuthenticationPrincipal User user) {
        ClusterResponse response = clusterService.createCluster(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a cluster")
    public ResponseEntity<Map<String, String>> deleteCluster(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        clusterService.deleteCluster(id, user);
        return ResponseEntity.ok(Map.of("message", "Cluster deletion initiated"));
    }

    @GetMapping("/{id}/health")
    @Operation(summary = "Get cluster health status")
    public ResponseEntity<ClusterHealthResponse> getClusterHealth(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        ClusterHealthResponse response = clusterService.getClusterHealth(id, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/credentials")
    @Operation(summary = "Get cluster credentials (sensitive - access is logged)")
    public ResponseEntity<ClusterCredentialsResponse> getClusterCredentials(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        ClusterCredentialsResponse response = clusterService.getClusterCredentials(id, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get cluster metrics from Prometheus")
    public ResponseEntity<ClusterMetricsResponse> getClusterMetrics(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1h") String range,
            @AuthenticationPrincipal User user) {
        ClusterMetricsResponse response = metricsService.getClusterMetrics(id, user, range);
        return ResponseEntity.ok(response);
    }
}
