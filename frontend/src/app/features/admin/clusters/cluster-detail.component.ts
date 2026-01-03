import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AdminService } from '../../../core/services/admin.service';
import { AdminClusterDetail } from '../../../core/models';

@Component({
  selector: 'app-admin-cluster-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="p-6">
      <!-- Back button -->
      <a routerLink="/admin/clusters" class="inline-flex items-center text-sm text-muted-foreground hover:text-foreground mb-4">
        <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
        </svg>
        Back to Clusters
      </a>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </div>
      } @else if (cluster()) {
        <!-- Header -->
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-2xl font-bold text-foreground">{{ cluster()!.name }}</h1>
            <p class="text-muted-foreground font-mono text-sm">{{ cluster()!.slug }}.db.pgcluster.com</p>
          </div>
          <span [class]="getStatusClass(cluster()!.status)" class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium">
            {{ cluster()!.status }}
          </span>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <!-- Cluster Info -->
          <div class="bg-card rounded-lg border border-border p-6">
            <h2 class="text-lg font-semibold text-foreground mb-4">Cluster Information</h2>
            <div class="space-y-3">
              <div class="flex justify-between">
                <span class="text-muted-foreground">Plan</span>
                <span class="text-foreground capitalize">{{ formatPlan(cluster()!.plan) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Region</span>
                <span class="text-foreground uppercase">{{ cluster()!.region }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">PostgreSQL</span>
                <span class="text-foreground">{{ cluster()!.postgres_version }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Created</span>
                <span class="text-foreground">{{ formatDate(cluster()!.created_at) }}</span>
              </div>
            </div>
          </div>

          <!-- Resources -->
          <div class="bg-card rounded-lg border border-border p-6">
            <h2 class="text-lg font-semibold text-foreground mb-4">Resources</h2>
            <div class="space-y-3">
              <div class="flex justify-between">
                <span class="text-muted-foreground">Nodes</span>
                <span class="text-foreground">{{ cluster()!.node_count }} x {{ cluster()!.node_size }}</span>
              </div>
              @if (cluster()!.resources) {
                <div class="flex justify-between">
                  <span class="text-muted-foreground">CPU</span>
                  <span class="text-foreground">{{ cluster()!.resources!.cpu_cores }} cores</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Memory</span>
                  <span class="text-foreground">{{ cluster()!.resources!.memory_mb }} MB</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Storage</span>
                  <span class="text-foreground">{{ cluster()!.resources!.storage_gb }} GB</span>
                </div>
              }
            </div>
          </div>

          <!-- Connection String -->
          <div class="bg-card rounded-lg border border-border p-6 lg:col-span-2">
            <div class="flex items-center justify-between mb-4">
              <h2 class="text-lg font-semibold text-foreground">Connection String</h2>
              @if (cluster()!.connection?.password && cluster()!.connection?.hostname) {
                <button (click)="togglePassword()" class="text-xs text-muted-foreground hover:text-foreground transition-colors">
                  {{ showPassword() ? 'Hide password' : 'Show password' }}
                </button>
              }
            </div>
            @if (cluster()!.connection?.connection_string) {
              <div class="bg-muted rounded-md px-3 py-2 font-mono text-sm overflow-x-auto whitespace-nowrap">
                @if (showPassword()) {
                  {{ cluster()!.connection!.connection_string }}
                } @else {
                  postgresql://postgres:<span class="password-mask"></span>&#64;{{ cluster()!.connection!.hostname }}:{{ cluster()!.connection!.port }}/postgres
                }
              </div>
            } @else {
              <p class="text-muted-foreground">Connection string not available</p>
            }
          </div>

          <!-- Cluster Nodes -->
          @if (cluster()!.nodes && cluster()!.nodes!.length > 0) {
            <div class="bg-card rounded-lg border border-border p-6 lg:col-span-2">
              <h2 class="text-lg font-semibold text-foreground mb-4">Cluster Nodes</h2>
              <div class="overflow-x-auto">
                <table class="w-full">
                  <thead class="bg-muted/50">
                    <tr>
                      <th class="text-left p-3 font-medium text-muted-foreground">Name</th>
                      <th class="text-left p-3 font-medium text-muted-foreground">Role</th>
                      <th class="text-left p-3 font-medium text-muted-foreground">Public IP</th>
                      <th class="text-left p-3 font-medium text-muted-foreground">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (node of cluster()!.nodes; track node.name) {
                      <tr class="border-t border-border">
                        <td class="p-3 font-medium text-foreground">{{ node.name }}</td>
                        <td class="p-3">
                          <span [class]="getRoleClass(node.role)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                            {{ node.role }}
                          </span>
                        </td>
                        <td class="p-3 font-mono text-sm text-muted-foreground">{{ node.public_ip }}</td>
                        <td class="p-3">
                          <span class="text-sm text-muted-foreground">{{ node.status || 'unknown' }}</span>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }
        </div>
      } @else {
        <div class="text-center py-12 bg-muted/50 rounded-lg">
          <p class="text-muted-foreground">Cluster not found</p>
        </div>
      }
    </div>
  `,
  styles: [`
    .password-mask {
      display: inline-block;
      width: 4em;
      height: 0.5em;
      background: currentColor;
      border-radius: 2px;
      vertical-align: middle;
      margin: 0 1px;
    }
  `]
})
export class AdminClusterDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private adminService = inject(AdminService);

  loading = signal(true);
  cluster = signal<AdminClusterDetail | null>(null);
  showPassword = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.adminService.getAdminCluster(id).subscribe({
        next: (cluster) => {
          this.cluster.set(cluster);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
    }
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'running':
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400';
      case 'creating':
      case 'provisioning':
      case 'forming':
        return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400';
      case 'error':
      case 'failed':
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400';
    }
  }

  getRoleClass(role: string): string {
    if (role === 'leader') {
      return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400';
    }
    return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
  }

  formatPlan(plan: string): string {
    return plan.replace('dedicated-', '');
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
