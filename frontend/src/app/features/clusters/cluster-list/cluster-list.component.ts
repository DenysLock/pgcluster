import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClusterService } from '../../../core/services/cluster.service';
import { StatusBadgeComponent, SpinnerComponent, EmptyStateComponent } from '../../../shared/components';

@Component({
  selector: 'app-cluster-list',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent, SpinnerComponent, EmptyStateComponent],
  template: `
    <div class="space-y-6">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-lg font-bold tracking-tight">Clusters</h1>
          <p class="text-sm text-muted-foreground">Manage your PostgreSQL clusters</p>
        </div>
        <a routerLink="/clusters/new" class="btn-primary">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create Cluster
        </a>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else if (clusters().length === 0) {
        <app-empty-state
          title="No clusters yet"
          description="Get started by creating your first PostgreSQL cluster"
        >
          <svg icon class="w-6 h-6 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
          </svg>
          <a action routerLink="/clusters/new" class="btn-primary">
            Create your first cluster
          </a>
        </app-empty-state>
      } @else {
        <!-- Clusters Table -->
        <div class="card p-0">
          <table class="w-full">
            <thead>
              <tr class="border-b bg-bg-tertiary">
                <th class="h-12 px-4 text-left align-middle text-xs font-semibold uppercase tracking-wide text-muted-foreground">Name</th>
                <th class="h-12 px-4 text-left align-middle text-xs font-semibold uppercase tracking-wide text-muted-foreground">Status</th>
                <th class="h-12 px-4 text-left align-middle text-xs font-semibold uppercase tracking-wide text-muted-foreground">Plan</th>
                <th class="h-12 px-4 text-left align-middle text-xs font-semibold uppercase tracking-wide text-muted-foreground">Created</th>
                <th class="h-12 px-4 text-left align-middle text-xs font-semibold uppercase tracking-wide text-muted-foreground"></th>
              </tr>
            </thead>
            <tbody>
              @for (cluster of clusters(); track cluster.id) {
                <tr class="border-b last:border-0 hover:bg-bg-tertiary transition-colors">
                  <td class="p-4">
                    <div>
                      <div class="flex items-center gap-2">
                        <p class="text-sm font-medium">{{ cluster.name }}</p>
                        <span class="badge badge-info">PG {{ cluster.postgresVersion }}</span>
                      </div>
                      <p class="text-xs text-muted-foreground">{{ cluster.slug }}.db.pgcluster.com</p>
                    </div>
                  </td>
                  <td class="p-4">
                    <app-status-badge [status]="cluster.status" />
                  </td>
                  <td class="p-4">
                    <span class="text-sm capitalize">{{ cluster.plan }}</span>
                  </td>
                  <td class="p-4">
                    <span class="text-xs text-muted-foreground">{{ formatDate(cluster.createdAt) }}</span>
                  </td>
                  <td class="p-4 text-right">
                    <a [routerLink]="['/clusters', cluster.id]" class="btn-secondary text-sm py-1 px-3">
                      View
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class ClusterListComponent implements OnInit {
  private clusterService = inject(ClusterService);

  loading = signal(true);
  clusters = this.clusterService.clusters;

  ngOnInit(): void {
    this.loadClusters();
  }

  private loadClusters(): void {
    this.clusterService.getClusters().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }
}
