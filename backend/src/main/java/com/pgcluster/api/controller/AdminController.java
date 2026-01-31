package com.pgcluster.api.controller;

import com.pgcluster.api.model.dto.*;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.service.AdminService;
import com.pgcluster.api.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Platform administration endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;

    // ==================== Stats ====================

    @GetMapping("/stats")
    @Operation(summary = "Get platform statistics")
    public ResponseEntity<AdminStatsResponse> getStats() {
        AdminStatsResponse response = adminService.getStats();
        return ResponseEntity.ok(response);
    }

    // ==================== Clusters ====================

    @GetMapping("/clusters")
    @Operation(summary = "List all clusters across all users")
    public ResponseEntity<AdminClusterListResponse> listAllClusters(
            @RequestParam(defaultValue = "true") boolean includeDeleted) {
        AdminClusterListResponse response = adminService.listAllClusters(includeDeleted);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clusters/{id}")
    @Operation(summary = "Get specific cluster details (any user's cluster)")
    public ResponseEntity<AdminClusterResponse> getCluster(@PathVariable UUID id) {
        AdminClusterResponse response = adminService.getCluster(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/clusters/{id}")
    @Operation(summary = "Delete any user's cluster")
    public ResponseEntity<Void> deleteCluster(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        adminService.deleteClusterAsAdmin(id, admin);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/clusters/{id}/credentials")
    @Operation(summary = "Get any cluster's credentials (admin access)")
    public ResponseEntity<ClusterCredentialsResponse> getClusterCredentials(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        ClusterCredentialsResponse response = adminService.getClusterCredentialsAsAdmin(id, admin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clusters/{id}/backups")
    @Operation(summary = "List backups for any cluster (admin access)")
    public ResponseEntity<BackupListResponse> getClusterBackups(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        List<Backup> backups = adminService.getClusterBackupsAsAdmin(id, includeDeleted);
        return ResponseEntity.ok(BackupListResponse.fromEntities(backups));
    }

    @DeleteMapping("/clusters/{id}/backups/{backupId}")
    @Operation(summary = "Delete a backup from any cluster (admin access)")
    public ResponseEntity<Map<String, String>> deleteClusterBackup(
            @PathVariable UUID id,
            @PathVariable UUID backupId,
            @RequestParam(defaultValue = "false") boolean confirm,
            @AuthenticationPrincipal User admin) {
        adminService.deleteBackupAsAdmin(id, backupId, confirm, admin);
        return ResponseEntity.ok(Map.of("message", "Backup deleted successfully"));
    }

    @GetMapping("/clusters/{id}/exports")
    @Operation(summary = "List exports for any cluster (admin access)")
    public ResponseEntity<List<ExportResponse>> getClusterExports(@PathVariable UUID id) {
        List<Export> exports = adminService.getClusterExportsAsAdmin(id);
        List<ExportResponse> responses = exports.stream()
                .map(ExportResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/clusters/{id}/exports/{exportId}")
    @Operation(summary = "Delete an export from any cluster (admin access)")
    public ResponseEntity<Map<String, String>> deleteClusterExport(
            @PathVariable UUID id,
            @PathVariable UUID exportId,
            @AuthenticationPrincipal User admin) {
        adminService.deleteExportAsAdmin(id, exportId, admin);
        return ResponseEntity.ok(Map.of("message", "Export deleted successfully"));
    }

    @GetMapping("/clusters/{id}/metrics")
    @Operation(summary = "Get metrics for any cluster (admin access)")
    public ResponseEntity<ClusterMetricsResponse> getClusterMetrics(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1h") String range) {
        ClusterMetricsResponse response = adminService.getClusterMetricsAsAdmin(id, range);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clusters/{id}/health")
    @Operation(summary = "Get cluster health status (admin access)")
    public ResponseEntity<ClusterHealthResponse> getClusterHealth(@PathVariable UUID id) {
        ClusterHealthResponse response = adminService.getClusterHealthAsAdmin(id);
        return ResponseEntity.ok(response);
    }

    // ==================== Users ====================

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public ResponseEntity<AdminUserListResponse> listAllUsers() {
        AdminUserListResponse response = adminService.listAllUsers();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users")
    @Operation(summary = "Create a new user (admin sets credentials)")
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal User admin) {
        AdminUserResponse response = adminService.createUser(request, admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user details with their clusters")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable UUID id) {
        AdminUserDetailResponse response = adminService.getUserDetail(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/disable")
    @Operation(summary = "Disable a user account")
    public ResponseEntity<AdminUserResponse> disableUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        AdminUserResponse response = adminService.disableUser(id, admin);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/enable")
    @Operation(summary = "Enable a user account")
    public ResponseEntity<AdminUserResponse> enableUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        AdminUserResponse response = adminService.enableUser(id, admin);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "Reset user password (admin sets new password)")
    public ResponseEntity<Void> resetUserPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal User admin) {
        adminService.resetUserPassword(id, request, admin);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{id}/activity")
    @Operation(summary = "Get user activity timeline")
    public ResponseEntity<List<AuditLogResponse>> getUserActivity(@PathVariable UUID id) {
        List<AuditLogResponse> activity = auditLogService.getUserActivity(id);
        return ResponseEntity.ok(activity);
    }

    // ==================== Audit Logs ====================

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs with filters")
    public ResponseEntity<AuditLogListResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID clusterId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        filter.setUserId(userId);
        filter.setClusterId(clusterId);
        filter.setAction(action);
        filter.setResourceType(resourceType);

        // Parse dates from ISO strings
        if (startDate != null && !startDate.isBlank()) {
            filter.setStartDate(Instant.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            filter.setEndDate(Instant.parse(endDate));
        }

        AuditLogListResponse response = auditLogService.getAuditLogs(filter, page, size);
        return ResponseEntity.ok(response);
    }
}
