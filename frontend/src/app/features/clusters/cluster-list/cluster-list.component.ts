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
          <h1 class="text-2xl font-bold tracking-tight">Clusters</h1>
          <p class="text-muted-foreground">Manage your PostgreSQL clusters</p>
        </div>
        <a
          routerLink="/clusters/new"
          class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
        >
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
          <a
            action
            routerLink="/clusters/new"
            class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
          >
            Create your first cluster
          </a>
        </app-empty-state>
      } @else {
        <!-- Clusters Table -->
        <div class="rounded-lg border bg-card">
          <table class="w-full">
            <thead>
              <tr class="border-b bg-muted/50">
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Name</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Status</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Plan</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Created</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground"></th>
              </tr>
            </thead>
            <tbody>
              @for (cluster of clusters(); track cluster.id) {
                <tr class="border-b last:border-0 hover:bg-muted/50 transition-colors">
                  <td class="p-4">
                    <div>
                      <div class="flex items-center gap-2">
                        <p class="font-medium">{{ cluster.name }}</p>
                        <span class="inline-flex items-center px-2 py-0.5 text-xs font-semibold uppercase border border-neon-cyan text-neon-cyan">PG {{ cluster.postgresVersion }}</span>
                      </div>
                      <p class="text-sm text-muted-foreground">{{ cluster.slug }}.db.pgcluster.com</p>
                    </div>
                  </td>
                  <td class="p-4">
                    <app-status-badge [status]="cluster.status" size="sm" />
                  </td>
                  <td class="p-4">
                    <span class="text-sm capitalize">{{ cluster.plan }}</span>
                  </td>
                  <td class="p-4">
                    <span class="text-sm text-muted-foreground">{{ formatDate(cluster.createdAt) }}</span>
                  </td>
                  <td class="p-4 text-right">
                    <a
                      [routerLink]="['/clusters', cluster.id]"
                      class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-9 px-3"
                    >
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
