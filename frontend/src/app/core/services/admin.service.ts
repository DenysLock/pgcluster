import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PlatformStats,
  AdminCluster,
  AdminClusterDetail,
  AdminClustersResponse,
  AdminUsersResponse,
  User,
  UserDetail,
  CreateUserRequest,
  ResetPasswordRequest,
  AuditLog,
  AuditLogListResponse,
  ClusterCredentials,
  Backup,
  Export,
  ClusterMetrics,
  ClusterHealth
} from '../models';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private clustersSignal = signal<AdminCluster[]>([]);
  private usersSignal = signal<User[]>([]);

  readonly clusters = this.clustersSignal.asReadonly();
  readonly users = this.usersSignal.asReadonly();

  constructor(private http: HttpClient) {}

  // ==================== Platform Stats ====================

  getPlatformStats(): Observable<PlatformStats> {
    return this.http.get<PlatformStats>(`${environment.apiUrl}/api/v1/admin/stats`);
  }

  // ==================== Cluster Management ====================

  getAdminClusters(): Observable<AdminClustersResponse> {
    return this.http.get<AdminClustersResponse>(`${environment.apiUrl}/api/v1/admin/clusters`).pipe(
      tap(response => this.clustersSignal.set(response.clusters || []))
    );
  }

  getAdminCluster(id: string): Observable<AdminClusterDetail> {
    return this.http.get<AdminClusterDetail>(`${environment.apiUrl}/api/v1/admin/clusters/${id}`);
  }

  deleteCluster(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/api/v1/admin/clusters/${id}`).pipe(
      tap(() => this.refreshClusters())
    );
  }

  refreshClusters(): void {
    this.getAdminClusters().subscribe();
  }

  // ==================== User Management ====================

  getUsers(): Observable<AdminUsersResponse> {
    return this.http.get<AdminUsersResponse>(`${environment.apiUrl}/api/v1/admin/users`).pipe(
      tap(response => this.usersSignal.set(response.users || []))
    );
  }

  getUserDetail(id: string): Observable<UserDetail> {
    return this.http.get<UserDetail>(`${environment.apiUrl}/api/v1/admin/users/${id}`);
  }

  createUser(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(`${environment.apiUrl}/api/v1/admin/users`, request).pipe(
      tap(() => this.refreshUsers())
    );
  }

  disableUser(id: string): Observable<User> {
    return this.http.post<User>(`${environment.apiUrl}/api/v1/admin/users/${id}/disable`, {}).pipe(
      tap(() => this.refreshUsers())
    );
  }

  enableUser(id: string): Observable<User> {
    return this.http.post<User>(`${environment.apiUrl}/api/v1/admin/users/${id}/enable`, {}).pipe(
      tap(() => this.refreshUsers())
    );
  }

  resetUserPassword(id: string, request: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/api/v1/admin/users/${id}/reset-password`, request);
  }

  private refreshUsers(): void {
    this.getUsers().subscribe();
  }

  // ==================== Audit Logs ====================

  getAuditLogs(params: {
    page?: number;
    size?: number;
    userId?: string;
    clusterId?: string;
    action?: string;
    resourceType?: string;
    startDate?: string;
    endDate?: string;
  } = {}): Observable<AuditLogListResponse> {
    let httpParams = new HttpParams();

    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
    if (params.userId) httpParams = httpParams.set('userId', params.userId);
    if (params.clusterId) httpParams = httpParams.set('clusterId', params.clusterId);
    if (params.action) httpParams = httpParams.set('action', params.action);
    if (params.resourceType) httpParams = httpParams.set('resourceType', params.resourceType);
    if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
    if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);

    return this.http.get<AuditLogListResponse>(`${environment.apiUrl}/api/v1/admin/audit-logs`, { params: httpParams });
  }

  getUserActivity(userId: string): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${environment.apiUrl}/api/v1/admin/users/${userId}/activity`);
  }

  // ==================== Admin Cluster Access ====================

  getClusterCredentials(clusterId: string): Observable<ClusterCredentials> {
    return this.http.get<ClusterCredentials>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/credentials`);
  }

  getClusterBackups(clusterId: string, includeDeleted = false): Observable<{ backups: Backup[] }> {
    const params = new HttpParams().set('includeDeleted', includeDeleted.toString());
    return this.http.get<{ backups: Backup[] }>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/backups`, { params });
  }

  deleteClusterBackup(clusterId: string, backupId: string, confirm = false): Observable<void> {
    const params = new HttpParams().set('confirm', confirm.toString());
    return this.http.delete<void>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/backups/${backupId}`, { params });
  }

  getClusterExports(clusterId: string): Observable<Export[]> {
    return this.http.get<Export[]>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/exports`);
  }

  deleteClusterExport(clusterId: string, exportId: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/exports/${exportId}`);
  }

  getClusterMetrics(clusterId: string, range = '1h'): Observable<ClusterMetrics> {
    const params = new HttpParams().set('range', range);
    return this.http.get<ClusterMetrics>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/metrics`, { params });
  }

  getClusterHealth(clusterId: string): Observable<ClusterHealth> {
    return this.http.get<ClusterHealth>(`${environment.apiUrl}/api/v1/admin/clusters/${clusterId}/health`);
  }
}
