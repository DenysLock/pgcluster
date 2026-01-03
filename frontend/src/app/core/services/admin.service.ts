import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PlatformStats,
  AdminCluster,
  AdminClusterDetail,
  AdminClustersResponse,
  AdminUsersResponse
} from '../models';
import { User } from '../models';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private clustersSignal = signal<AdminCluster[]>([]);
  private usersSignal = signal<User[]>([]);

  readonly clusters = this.clustersSignal.asReadonly();
  readonly users = this.usersSignal.asReadonly();

  constructor(private http: HttpClient) {}

  // Cluster Management (Admin)
  getAdminClusters(): Observable<AdminClustersResponse> {
    return this.http.get<AdminClustersResponse>(`${environment.apiUrl}/api/v1/admin/clusters`).pipe(
      tap(response => this.clustersSignal.set(response.clusters || []))
    );
  }

  getAdminCluster(id: string): Observable<AdminClusterDetail> {
    return this.http.get<AdminClusterDetail>(`${environment.apiUrl}/api/v1/admin/clusters/${id}`);
  }

  refreshClusters(): void {
    this.getAdminClusters().subscribe();
  }

  // User Management
  getUsers(): Observable<AdminUsersResponse> {
    return this.http.get<AdminUsersResponse>(`${environment.apiUrl}/api/v1/admin/users`).pipe(
      tap(response => this.usersSignal.set(response.users || []))
    );
  }

  // Platform Stats
  getPlatformStats(): Observable<PlatformStats> {
    return this.http.get<PlatformStats>(`${environment.apiUrl}/api/v1/admin/stats`);
  }
}
