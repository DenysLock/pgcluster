package com.pgcluster.api.controller;

import com.pgcluster.api.model.dto.BackupDeletionInfo;
import com.pgcluster.api.model.dto.BackupListResponse;
import com.pgcluster.api.model.dto.BackupResponse;
import com.pgcluster.api.model.dto.ExportResponse;
import com.pgcluster.api.model.dto.RestoreJobResponse;
import com.pgcluster.api.model.dto.RestoreRequest;
import com.pgcluster.api.model.entity.Backup;
import com.pgcluster.api.model.entity.Export;
import com.pgcluster.api.model.entity.RestoreJob;
import com.pgcluster.api.model.entity.User;
import com.pgcluster.api.service.BackupService;
import com.pgcluster.api.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/backups")
@RequiredArgsConstructor
@Tag(name = "Backups", description = "Backup and restore management")
@SecurityRequirement(name = "bearerAuth")
public class BackupController {

    private final BackupService backupService;
    private final ExportService exportService;

    @GetMapping
    @Operation(summary = "List all backups for a cluster (excludes deleted by default)")
    public ResponseEntity<BackupListResponse> listBackups(
            @PathVariable UUID clusterId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @AuthenticationPrincipal User user) {
        List<Backup> backups = backupService.listBackups(clusterId, user, includeDeleted);
        return ResponseEntity.ok(BackupListResponse.fromEntities(backups));
    }

    @PostMapping
    @Operation(summary = "Create a manual backup")
    public ResponseEntity<BackupResponse> createBackup(
            @PathVariable UUID clusterId,
            @RequestParam(required = false) String backupType,
            @AuthenticationPrincipal User user) {
        Backup backup = backupService.createBackup(clusterId, user, backupType);
        return ResponseEntity.status(HttpStatus.CREATED).body(BackupResponse.fromEntity(backup));
    }

    @GetMapping("/{backupId}")
    @Operation(summary = "Get backup details")
    public ResponseEntity<BackupResponse> getBackup(
            @PathVariable UUID clusterId,
            @PathVariable UUID backupId,
            @AuthenticationPrincipal User user) {
        Backup backup = backupService.getBackup(clusterId, backupId, user);
        return ResponseEntity.ok(BackupResponse.fromEntity(backup));
    }

    @GetMapping("/{backupId}/deletion-info")
    @Operation(summary = "Get information about what will be deleted (for confirmation dialog)")
    public ResponseEntity<BackupDeletionInfo> getBackupDeletionInfo(
            @PathVariable UUID clusterId,
            @PathVariable UUID backupId,
            @AuthenticationPrincipal User user) {
        BackupDeletionInfo info = backupService.getBackupDeletionInfo(clusterId, backupId, user);
        return ResponseEntity.ok(info);
    }

    @DeleteMapping("/{backupId}")
    @Operation(summary = "Delete a backup and its dependents (requires confirm=true if has dependents)")
    public ResponseEntity<Map<String, String>> deleteBackup(
            @PathVariable UUID clusterId,
            @PathVariable UUID backupId,
            @RequestParam(defaultValue = "false") boolean confirm,
            @AuthenticationPrincipal User user) {
        backupService.deleteBackup(clusterId, backupId, user, confirm);
        return ResponseEntity.ok(Map.of("message", "Backup deleted successfully"));
    }

    @PostMapping("/{backupId}/restore")
    @Operation(summary = "Restore from a backup (creates a new cluster)")
    public ResponseEntity<RestoreJobResponse> restoreBackup(
            @PathVariable UUID clusterId,
            @PathVariable UUID backupId,
            @Valid @RequestBody(required = false) RestoreRequest request,
            @AuthenticationPrincipal User user) {
        RestoreJob job = backupService.restoreBackup(clusterId, backupId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(RestoreJobResponse.fromEntity(job));
    }

    @GetMapping("/restore-jobs")
    @Operation(summary = "List all restore jobs for a cluster")
    public ResponseEntity<List<RestoreJobResponse>> listRestoreJobs(
            @PathVariable UUID clusterId,
            @AuthenticationPrincipal User user) {
        List<RestoreJob> jobs = backupService.listRestoreJobs(clusterId, user);
        List<RestoreJobResponse> responses = jobs.stream()
                .map(RestoreJobResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/restore-jobs/{jobId}")
    @Operation(summary = "Get restore job details")
    public ResponseEntity<RestoreJobResponse> getRestoreJob(
            @PathVariable UUID clusterId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal User user) {
        RestoreJob job = backupService.getRestoreJob(clusterId, jobId, user);
        return ResponseEntity.ok(RestoreJobResponse.fromEntity(job));
    }

    // ============ Export Endpoints ============

    @GetMapping("/exports")
    @Operation(summary = "List all exports for a cluster")
    public ResponseEntity<List<ExportResponse>> listExports(
            @PathVariable UUID clusterId,
            @AuthenticationPrincipal User user) {
        List<Export> exports = exportService.listExports(clusterId, user);
        List<ExportResponse> responses = exports.stream()
                .map(ExportResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/exports")
    @Operation(summary = "Create a database export (pg_dump)")
    public ResponseEntity<ExportResponse> createExport(
            @PathVariable UUID clusterId,
            @AuthenticationPrincipal User user) {
        Export export = exportService.createExport(clusterId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ExportResponse.fromEntity(export));
    }

    @GetMapping("/exports/{exportId}")
    @Operation(summary = "Get export details")
    public ResponseEntity<ExportResponse> getExport(
            @PathVariable UUID clusterId,
            @PathVariable UUID exportId,
            @AuthenticationPrincipal User user) {
        Export export = exportService.getExport(clusterId, exportId, user);
        return ResponseEntity.ok(ExportResponse.fromEntity(export));
    }

    @PostMapping("/exports/{exportId}/refresh-url")
    @Operation(summary = "Refresh download URL for an export")
    public ResponseEntity<ExportResponse> refreshExportUrl(
            @PathVariable UUID clusterId,
            @PathVariable UUID exportId,
            @AuthenticationPrincipal User user) {
        Export export = exportService.refreshDownloadUrl(clusterId, exportId, user);
        return ResponseEntity.ok(ExportResponse.fromEntity(export));
    }

    @DeleteMapping("/exports/{exportId}")
    @Operation(summary = "Delete an export")
    public ResponseEntity<Map<String, String>> deleteExport(
            @PathVariable UUID clusterId,
            @PathVariable UUID exportId,
            @AuthenticationPrincipal User user) {
        exportService.deleteExport(clusterId, exportId, user);
        return ResponseEntity.ok(Map.of("message", "Export deleted successfully"));
    }

    // ============ Metrics Endpoint ============

    @GetMapping("/metrics")
    @Operation(summary = "Get backup storage metrics for a cluster")
    public ResponseEntity<Map<String, Object>> getBackupMetrics(
            @PathVariable UUID clusterId,
            @AuthenticationPrincipal User user) {
        Map<String, Object> metrics = backupService.getBackupMetrics(clusterId, user);
        return ResponseEntity.ok(metrics);
    }
}
