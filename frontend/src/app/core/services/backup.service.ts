import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Backup,
  BackupDeletionInfo,
  BackupListResponse,
  BackupMetrics,
  Export,
  PitrRestoreRequest,
  PitrWindowResponse,
  RestoreJob,
  RestoreRequest
} from '../models';

@Injectable({
  providedIn: 'root'
})
export class BackupService {

  constructor(private http: HttpClient) {}

  /**
   * Get all backups for a cluster
   */
  getBackups(clusterId: string): Observable<BackupListResponse> {
    return this.http.get<BackupListResponse>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups`
    );
  }

  /**
   * Get a specific backup
   */
  getBackup(clusterId: string, backupId: string): Observable<Backup> {
    return this.http.get<Backup>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/${backupId}`
    );
  }

  /**
   * Create a manual backup
   * @param backupType Optional: 'full', 'diff', or 'incr' (default: 'incr')
   */
  createBackup(clusterId: string, backupType?: 'full' | 'diff' | 'incr'): Observable<Backup> {
    const url = `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups`;
    if (backupType) {
      return this.http.post<Backup>(url, {}, { params: { backupType } });
    }
    return this.http.post<Backup>(url, {});
  }

  /**
   * Get deletion info for a backup (shows what will be deleted)
   */
  getBackupDeletionInfo(clusterId: string, backupId: string): Observable<BackupDeletionInfo> {
    return this.http.get<BackupDeletionInfo>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/${backupId}/deletion-info`
    );
  }

  /**
   * Delete a backup (with optional confirmation for cascade deletion)
   */
  deleteBackup(clusterId: string, backupId: string, confirm: boolean = false): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/${backupId}`,
      { params: { confirm: confirm.toString() } }
    );
  }

  /**
   * Restore from a backup (creates a new cluster)
   */
  restoreBackup(clusterId: string, backupId: string, request?: RestoreRequest): Observable<RestoreJob> {
    return this.http.post<RestoreJob>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/${backupId}/restore`,
      request || {}
    );
  }

  /**
   * Get PITR availability window for a cluster.
   */
  getPitrWindow(clusterId: string): Observable<PitrWindowResponse> {
    return this.http.get<PitrWindowResponse>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/pitr/window`
    );
  }

  /**
   * Restore cluster from a selected PITR time.
   */
  restoreFromPitr(clusterId: string, request: PitrRestoreRequest): Observable<RestoreJob> {
    return this.http.post<RestoreJob>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/pitr/restore`,
      request
    );
  }

  /**
   * Get all restore jobs for a cluster
   */
  getRestoreJobs(clusterId: string): Observable<RestoreJob[]> {
    return this.http.get<RestoreJob[]>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/restore-jobs`
    );
  }

  /**
   * Get a specific restore job
   */
  getRestoreJob(clusterId: string, jobId: string): Observable<RestoreJob> {
    return this.http.get<RestoreJob>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/restore-jobs/${jobId}`
    );
  }

  /**
   * Get backup storage metrics
   */
  getBackupMetrics(clusterId: string): Observable<BackupMetrics> {
    return this.http.get<BackupMetrics>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/metrics`
    );
  }

  /**
   * Get all exports for a cluster
   */
  getExports(clusterId: string): Observable<Export[]> {
    return this.http.get<Export[]>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/exports`
    );
  }

  /**
   * Create a database export (pg_dump)
   */
  createExport(clusterId: string): Observable<Export> {
    return this.http.post<Export>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/exports`,
      {}
    );
  }

  /**
   * Get a specific export
   */
  getExport(clusterId: string, exportId: string): Observable<Export> {
    return this.http.get<Export>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/exports/${exportId}`
    );
  }

  /**
   * Refresh download URL for an export
   */
  refreshExportUrl(clusterId: string, exportId: string): Observable<Export> {
    return this.http.post<Export>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/exports/${exportId}/refresh-url`,
      {}
    );
  }

  /**
   * Delete an export
   */
  deleteExport(clusterId: string, exportId: string): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/backups/exports/${exportId}`
    );
  }
}
