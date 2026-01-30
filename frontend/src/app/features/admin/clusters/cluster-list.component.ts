import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../core/services/admin.service';
import { AdminCluster } from '../../../core/models';
import { SpinnerComponent, EmptyStateComponent } from '../../../shared/components';

@Component({
  selector: 'app-admin-cluster-list',
  standalone: true,
  imports: [CommonModule, RouterModule, SpinnerComponent, EmptyStateComponent],
  template: `
    <div class="p-6">
      <div class="mb-6">
        <h1 class="text-lg font-bold text-foreground">All Clusters</h1>
        <p class="text-muted-foreground mt-1">View all clusters across all users</p>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else if (clusters().length === 0) {
        <app-empty-state
          title="No clusters"
          description="No clusters have been created yet"
        >
          <svg icon class="w-6 h-6 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
          </svg>
        </app-empty-state>
      } @else {
        <div class="bg-card rounded-lg border border-border overflow-hidden">
          <table class="w-full">
            <thead class="bg-muted/50">
              <tr>
                <th class="text-left p-4 font-medium text-muted-foreground">Name</th>
                <th class="text-left p-4 font-medium text-muted-foreground">Region</th>
                <th class="text-left p-4 font-medium text-muted-foreground">Status</th>
                <th class="text-left p-4 font-medium text-muted-foreground">Plan</th>
                <th class="text-left p-4 font-medium text-muted-foreground">Created</th>
                <th class="text-right p-4 font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (cluster of clusters(); track cluster.id) {
                <tr class="border-t border-border hover:bg-muted/30 transition-colors">
                  <td class="p-4">
                    <div class="font-medium text-foreground">{{ cluster.name }}</div>
                    <div class="text-xs text-muted-foreground font-mono">{{ cluster.slug }}.db.pgcluster.com</div>
                  </td>
                  <td class="p-4">
                    <span class="text-sm text-muted-foreground">{{ cluster.region }}</span>
                  </td>
                  <td class="p-4">
                    <span [class]="getStatusClass(cluster.status)" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium">
                      {{ cluster.status }}
                    </span>
                  </td>
                  <td class="p-4">
                    <span class="text-sm text-muted-foreground capitalize">{{ formatPlan(cluster.plan) }}</span>
                  </td>
                  <td class="p-4">
                    <span class="text-sm text-muted-foreground">{{ formatDate(cluster.created_at) }}</span>
                  </td>
                  <td class="p-4 text-right">
                    <a [routerLink]="['/admin/clusters', cluster.id]"
                       class="inline-flex items-center px-3 py-1.5 text-sm font-medium text-primary hover:bg-primary/10 rounded-md transition-colors">
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
export class AdminClusterListComponent implements OnInit {
  private adminService = inject(AdminService);

  loading = signal(true);
  clusters = this.adminService.clusters;

  ngOnInit(): void {
    this.adminService.getAdminClusters().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'running':
        return 'bg-green-50 text-green-700 border border-green-500';
      case 'creating':
      case 'provisioning':
      case 'forming':
        return 'bg-amber-50 text-amber-700 border border-amber-500';
      case 'error':
      case 'failed':
        return 'bg-red-50 text-red-700 border border-red-500';
      case 'degraded':
        return 'bg-orange-50 text-orange-700 border border-orange-500';
      default:
        return 'bg-slate-100 text-slate-600 border border-slate-300';
    }
  }

  formatPlan(plan: string): string {
    return plan.replace('dedicated-', '');
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }
}
