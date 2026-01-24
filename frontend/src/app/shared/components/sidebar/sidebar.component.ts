import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ClusterService } from '../../../core/services/cluster.service';
import { Cluster } from '../../../core/models/cluster.model';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <aside class="w-64 bg-bg-secondary border-r border-border flex flex-col h-full">
      <!-- Add Cluster Button -->
      <div class="p-4">
        <a
          routerLink="/clusters/new"
          class="btn-primary w-full text-center block py-4 text-base"
          [class.bg-neon-green]="isCreateRoute()"
          [class.text-bg-primary]="isCreateRoute()"
        >
          + ADD CLUSTER
        </a>
      </div>

      <!-- Search -->
      <div class="px-4 pb-4">
        <input
          type="text"
          [(ngModel)]="searchQuery"
          placeholder="Search clusters..."
          class="input text-sm py-3"
        />
      </div>

      <!-- Cluster List -->
      <div class="flex-1 overflow-y-auto">
        @if (loading()) {
          <div class="flex items-center justify-center py-8">
            <div class="spinner w-6 h-6"></div>
          </div>
        } @else if (filteredClusters().length === 0) {
          <div class="px-4 py-8 text-center text-muted-foreground text-sm">
            @if (searchQuery()) {
              No clusters match your search
            } @else {
              No clusters yet
            }
          </div>
        } @else {
          <nav class="px-2">
            @for (cluster of filteredClusters(); track cluster.id) {
              <a
                [routerLink]="['/clusters', cluster.id]"
                class="flex items-center justify-between px-3 py-3 transition-colors group border-b border-border"
                [class.bg-bg-tertiary]="isSelected(cluster.id)"
                [class.text-foreground]="isSelected(cluster.id)"
                [class.text-muted-foreground]="!isSelected(cluster.id)"
                [class.hover:bg-bg-tertiary]="!isSelected(cluster.id)"
                [class.hover:text-foreground]="!isSelected(cluster.id)"
              >
                <div class="flex-1 min-w-0 mr-3">
                  <div class="text-base font-medium truncate">{{ cluster.name }}</div>
                  <div class="text-sm text-muted-foreground truncate">{{ cluster.connection?.hostname || cluster.slug }}</div>
                </div>
                <span
                  class="status-dot flex-shrink-0"
                  [class.status-dot-running]="cluster.status === 'running'"
                  [class.status-dot-warning]="isWarningStatus(cluster.status)"
                  [class.status-dot-error]="isErrorStatus(cluster.status)"
                  [class.status-dot-stopped]="!isRunningStatus(cluster.status) && !isWarningStatus(cluster.status) && !isErrorStatus(cluster.status)"
                ></span>
              </a>
            }
          </nav>
        }
      </div>

      <!-- Footer -->
      <div class="p-4 border-t border-border">
        <div class="text-xs text-muted-foreground text-center">
          {{ filteredClusters().length }} cluster{{ filteredClusters().length !== 1 ? 's' : '' }}
        </div>
      </div>
    </aside>
  `,
})
export class SidebarComponent {
  private clusterService = inject(ClusterService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  searchQuery = signal('');
  loading = signal(true);

  clusters = this.clusterService.clusters;

  filteredClusters = computed(() => {
    const query = this.searchQuery().toLowerCase();
    const allClusters = this.clusters()
      .sort((a, b) => {
        // Sort by status (running first), then by name
        const statusOrder: Record<string, number> = {
          running: 0,
          creating: 1,
          provisioning: 1,
          forming: 1,
          pending: 1,
          degraded: 2,
          error: 3,
          deleting: 4,
        };
        const aOrder = statusOrder[a.status] ?? 10;
        const bOrder = statusOrder[b.status] ?? 10;
        if (aOrder !== bOrder) return aOrder - bOrder;
        return a.name.localeCompare(b.name);
      });

    if (!query) return allClusters;
    return allClusters.filter(c =>
      c.name.toLowerCase().includes(query) ||
      c.slug.toLowerCase().includes(query)
    );
  });

  constructor() {
    this.loadClusters();
  }

  async loadClusters() {
    this.loading.set(true);
    try {
      await this.clusterService.getClusters().toPromise();
    } finally {
      this.loading.set(false);
    }
  }

  isSelected(clusterId: string): boolean {
    return this.router.url.includes(clusterId);
  }

  isCreateRoute(): boolean {
    return this.router.url === '/clusters/new';
  }

  isRunningStatus(status: string): boolean {
    return status === 'running';
  }

  isWarningStatus(status: string): boolean {
    return ['pending', 'creating', 'provisioning', 'forming', 'degraded'].includes(status);
  }

  isErrorStatus(status: string): boolean {
    return status === 'error';
  }
}
