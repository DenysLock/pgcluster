import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Cluster,
  ClusterHealth,
  CreateClusterRequest,
  ClusterListResponse,
  ClusterApiResponse,
  toCluster
} from '../models';

@Injectable({
  providedIn: 'root'
})
export class ClusterService {
  private clustersSignal = signal<Cluster[]>([]);
  readonly clusters = this.clustersSignal.asReadonly();

  constructor(private http: HttpClient) {}

  getClusters(): Observable<Cluster[]> {
    return this.http.get<ClusterListResponse>(`${environment.apiUrl}/api/v1/clusters`).pipe(
      map(response => {
        const items = response?.clusters || [];
        return items.map(item => toCluster(item));
      }),
      tap(clusters => this.clustersSignal.set(clusters))
    );
  }

  getCluster(id: string): Observable<Cluster> {
    return this.http.get<ClusterApiResponse>(`${environment.apiUrl}/api/v1/clusters/${id}`).pipe(
      map(response => toCluster(response))
    );
  }

  createCluster(request: CreateClusterRequest): Observable<Cluster> {
    return this.http.post<ClusterApiResponse>(`${environment.apiUrl}/api/v1/clusters`, request).pipe(
      map(response => toCluster(response)),
      tap(cluster => {
        this.clustersSignal.update(clusters => [...clusters, cluster]);
      })
    );
  }

  deleteCluster(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/api/v1/clusters/${id}`).pipe(
      tap(() => {
        this.clustersSignal.update(clusters => clusters.filter(c => c.id !== id));
      })
    );
  }

  getClusterHealth(id: string): Observable<ClusterHealth> {
    return this.http.get<ClusterHealth>(`${environment.apiUrl}/api/v1/clusters/${id}/health`);
  }

  refreshClusters(): void {
    this.getClusters().subscribe();
  }
}
