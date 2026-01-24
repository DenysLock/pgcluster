import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClusterMetrics, TimeRange } from '../models';

@Injectable({
  providedIn: 'root'
})
export class MetricsService {

  constructor(private http: HttpClient) {}

  /**
   * Get cluster metrics from Prometheus
   * @param clusterId The cluster UUID
   * @param range Time range: '1h', '6h', '24h', '7d'
   */
  getClusterMetrics(clusterId: string, range: TimeRange = '1h'): Observable<ClusterMetrics> {
    return this.http.get<ClusterMetrics>(
      `${environment.apiUrl}/api/v1/clusters/${clusterId}/metrics`,
      { params: { range } }
    );
  }
}
