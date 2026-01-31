import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminService } from '../../../core/services/admin.service';
import { UserDetail, AuditLog } from '../../../core/models';
import { SpinnerComponent } from '../../../shared/components';

@Component({
  selector: 'app-user-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, SpinnerComponent],
  template: `
    <div class="space-y-6">
      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else if (user()) {
        <!-- Header -->
        <div class="flex items-center gap-4">
          <a routerLink="/admin/users" class="text-muted-foreground hover:text-foreground">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </a>
          <span class="text-muted-foreground">Back to Users</span>
        </div>

        <!-- User Info Card -->
        <div class="rounded-lg border bg-card p-6">
          <div class="flex items-start justify-between">
            <div class="flex items-center gap-4">
              <div class="w-16 h-16 rounded-full bg-muted flex items-center justify-center">
                <span class="text-2xl font-medium text-muted-foreground">
                  {{ user()!.email.charAt(0).toUpperCase() }}
                </span>
              </div>
              <div>
                <h1 class="text-xl font-bold">{{ user()!.email }}</h1>
                <div class="flex items-center gap-2 mt-1">
                  @if (user()!.active) {
                    <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-green-100 text-green-700 border border-green-200">
                      Active
                    </span>
                  } @else {
                    <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-red-100 text-red-700 border border-red-200">
                      Disabled
                    </span>
                  }
                  @if (user()!.role === 'admin') {
                    <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-purple-100 text-purple-700 border border-purple-200">
                      Admin
                    </span>
                  } @else {
                    <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-gray-100 text-gray-700 border border-gray-200">
                      User
                    </span>
                  }
                </div>
              </div>
            </div>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6 text-sm">
            <div>
              <span class="text-muted-foreground">Registered</span>
              <p class="font-medium">{{ formatDate(user()!.created_at) }}</p>
            </div>
            <div>
              <span class="text-muted-foreground">Clusters</span>
              <p class="font-medium">{{ user()!.cluster_count }}</p>
            </div>
            @if (user()!.first_name || user()!.last_name) {
              <div>
                <span class="text-muted-foreground">Name</span>
                <p class="font-medium">{{ user()!.first_name }} {{ user()!.last_name }}</p>
              </div>
            }
          </div>
        </div>

        <!-- User's Clusters -->
        <div class="rounded-lg border bg-card">
          <div class="px-6 py-4 border-b">
            <h2 class="font-semibold">Clusters</h2>
          </div>
          @if (user()!.clusters.length === 0) {
            <div class="p-6 text-center text-muted-foreground">
              This user has no clusters
            </div>
          } @else {
            <div class="divide-y">
              @for (cluster of user()!.clusters; track cluster.id) {
                <a [routerLink]="['/admin/clusters', cluster.id]" class="flex items-center justify-between p-4 hover:bg-muted/25">
                  <div>
                    <div class="font-medium">{{ cluster.name }}</div>
                    <div class="text-sm text-muted-foreground">{{ cluster.slug }}</div>
                  </div>
                  <span class="text-xs px-2 py-1 rounded-full" [ngClass]="getStatusClass(cluster.status)">
                    {{ cluster.status }}
                  </span>
                </a>
              }
            </div>
          }
        </div>

        <!-- Activity Timeline -->
        <div class="rounded-lg border bg-card">
          <div class="px-6 py-4 border-b">
            <h2 class="font-semibold">Recent Activity</h2>
          </div>
          @if (activity().length === 0) {
            <div class="p-6 text-center text-muted-foreground">
              No activity recorded
            </div>
          } @else {
            <div class="divide-y max-h-96 overflow-y-auto">
              @for (log of activity(); track log.id) {
                <div class="p-4">
                  <div class="flex items-center justify-between">
                    <span class="text-sm font-medium" [ngClass]="getActionClass(log.action)">
                      {{ formatAction(log.action) }}
                    </span>
                    <span class="text-xs text-muted-foreground">{{ formatTimestamp(log.timestamp) }}</span>
                  </div>
                  @if (log.resource_type) {
                    <div class="text-xs text-muted-foreground mt-1">
                      {{ log.resource_type }}:
                      @if (getClusterInfo(log)) {
                        <a [routerLink]="['/admin/clusters', getClusterInfo(log)!.id]"
                           class="text-primary hover:underline ml-1">
                          {{ getClusterInfo(log)!.name }}
                        </a>
                        @if (getRestoreTarget(log)) {
                          <span class="mx-1">→</span>
                          <a [routerLink]="['/admin/clusters', getRestoreTarget(log)!.id]"
                             class="text-primary hover:underline">
                            {{ getRestoreTarget(log)!.name }}
                          </a>
                        }
                        @if (getRestoredFrom(log)) {
                          <span class="ml-1">(from
                            <a [routerLink]="['/admin/clusters', getRestoredFrom(log)!.id]" class="text-primary hover:underline">
                              {{ getRestoredFrom(log)!.name }}
                            </a>)
                          </span>
                        }
                      } @else if (log.resource_id) {
                        {{ log.resource_id.substring(0, 8) }}...
                      }
                    </div>
                  }
                  @if (log.ip_address) {
                    <div class="text-xs text-muted-foreground mt-0.5 font-mono">
                      IP: {{ log.ip_address }}
                    </div>
                  }
                </div>
              }
            </div>
          }
        </div>
      } @else {
        <div class="text-center py-12">
          <p class="text-muted-foreground">User not found</p>
        </div>
      }
    </div>
  `
})
export class UserDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private adminService = inject(AdminService);

  loading = signal(true);
  user = signal<UserDetail | null>(null);
  activity = signal<AuditLog[]>([]);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadUser(id);
      this.loadActivity(id);
    }
  }

  private loadUser(id: string): void {
    this.adminService.getUserDetail(id).subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  private loadActivity(id: string): void {
    this.adminService.getUserActivity(id).subscribe({
      next: (activity) => this.activity.set(activity),
      error: () => {}
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric', month: 'short', day: 'numeric'
    });
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString('en-US', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  formatAction(action: string): string {
    return action.replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase());
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'running': return 'bg-green-100 text-green-700 border border-green-200';
      case 'creating':
      case 'provisioning': return 'bg-amber-100 text-amber-700 border border-amber-200';
      case 'error':
      case 'failed': return 'bg-red-100 text-red-700 border border-red-200';
      case 'deleted': return 'bg-gray-100 text-gray-500 border border-gray-200';
      default: return 'bg-gray-100 text-gray-700 border border-gray-200';
    }
  }

  getActionClass(action: string): string {
    if (action.includes('FAILURE') || action.includes('DELETED') || action.includes('DISABLED')) {
      return 'text-red-600';
    }
    if (action.includes('CREATED') || action.includes('SUCCESS') || action.includes('ENABLED')) {
      return 'text-green-600';
    }
    return 'text-foreground';
  }

  getClusterInfo(activity: AuditLog): { id: string; name: string } | null {
    if (!activity.details) return null;
    if (activity.resource_type === 'cluster' && activity.resource_id) {
      const name = activity.details['cluster_slug'] || activity.details['cluster_name'] || null;
      return name ? { id: activity.resource_id, name } : null;
    }
    if ((activity.resource_type === 'backup' || activity.resource_type === 'export') && activity.details['cluster_id']) {
      // Prefer cluster_slug over cluster_name for full slug display
      const name = activity.details['cluster_slug'] || activity.details['cluster_name'] || null;
      return name ? { id: activity.details['cluster_id'], name } : null;
    }
    return null;
  }

  // For BACKUP_RESTORE_INITIATED: get target cluster info
  getRestoreTarget(log: AuditLog): { id: string; name: string } | null {
    if (!log.details || log.action !== 'BACKUP_RESTORE_INITIATED') return null;
    const targetId = log.details['target_cluster_id'];
    const targetName = log.details['target_cluster_slug'] || log.details['target_cluster_name'];
    if (targetId && targetName && targetName !== 'in-place') {
      return { id: targetId, name: targetName };
    }
    return null;
  }

  // For CLUSTER_CREATED: get "restored from" info if cluster was created from restore
  getRestoredFrom(log: AuditLog): { id: string; name: string } | null {
    if (!log.details || log.action !== 'CLUSTER_CREATED') return null;
    const id = log.details['restored_from_cluster_id'];
    const name = log.details['restored_from_cluster_slug'];
    if (id && name) {
      return { id, name };
    }
    return null;
  }
}
