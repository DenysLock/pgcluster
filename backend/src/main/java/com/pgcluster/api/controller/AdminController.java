package com.pgcluster.api.controller;

import com.pgcluster.api.model.dto.AdminClusterListResponse;
import com.pgcluster.api.model.dto.AdminClusterResponse;
import com.pgcluster.api.model.dto.AdminStatsResponse;
import com.pgcluster.api.model.dto.AdminUserListResponse;
import com.pgcluster.api.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Platform administration endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    @Operation(summary = "Get platform statistics")
    public ResponseEntity<AdminStatsResponse> getStats() {
        AdminStatsResponse response = adminService.getStats();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clusters")
    @Operation(summary = "List all clusters across all users")
    public ResponseEntity<AdminClusterListResponse> listAllClusters() {
        AdminClusterListResponse response = adminService.listAllClusters();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clusters/{id}")
    @Operation(summary = "Get specific cluster details (any user's cluster)")
    public ResponseEntity<AdminClusterResponse> getCluster(@PathVariable UUID id) {
        AdminClusterResponse response = adminService.getCluster(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public ResponseEntity<AdminUserListResponse> listAllUsers() {
        AdminUserListResponse response = adminService.listAllUsers();
        return ResponseEntity.ok(response);
    }
}
