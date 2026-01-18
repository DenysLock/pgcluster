import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AdminService } from '../../../core/services/admin.service';
import { ClusterService } from '../../../core/services/cluster.service';
import { AdminClusterDetail, ClusterCredentials } from '../../../core/models';
import { SpinnerComponent, ConnectionStringComponent } from '../../../shared/components';

@Component({
  selector: 'app-admin-cluster-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, SpinnerComponent, ConnectionStringComponent],
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

          <!-- Connection Details -->
          <div class="bg-card rounded-lg border border-border p-6 lg:col-span-2">
            <h2 class="text-lg font-semibold text-foreground mb-4">Connection Details</h2>
            @if (cluster()!.connection) {
              <div class="space-y-4">
                <!-- Basic connection info -->
                <div class="grid gap-4 md:grid-cols-2">
                  <div class="bg-muted/50 rounded-md p-3">
                    <span class="text-xs text-muted-foreground">Host</span>
                    <p class="font-mono text-sm">{{ cluster()!.connection!.hostname }}</p>
                  </div>
                  <div class="bg-muted/50 rounded-md p-3">
                    <span class="text-xs text-muted-foreground">Port</span>
                    <p class="font-mono text-sm">{{ cluster()!.connection!.port }}</p>
                  </div>
                  <div class="bg-muted/50 rounded-md p-3">
                    <span class="text-xs text-muted-foreground">Username</span>
                    <p class="font-mono text-sm">{{ cluster()!.connection!.username }}</p>
                  </div>
                  <div class="bg-muted/50 rounded-md p-3">
                    <span class="text-xs text-muted-foreground">Database</span>
                    <p class="font-mono text-sm">postgres</p>
                  </div>
                </div>

                <!-- Credentials section -->
                @if (!credentials()) {
                  <div class="border-t pt-4">
                    <button
                      (click)="loadCredentials()"
                      [disabled]="credentialsLoading()"
                      class="inline-flex items-center px-3 py-2 text-sm font-medium rounded-md border border-input bg-background hover:bg-accent hover:text-accent-foreground transition-colors disabled:opacity-50"
                    >
                      @if (credentialsLoading()) {
                        <app-spinner size="sm" class="mr-2" />
                        Loading...
                      } @else {
                        <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                        Show Credentials
                      }
                    </button>
                    <p class="text-xs text-muted-foreground mt-2">Access is logged for security.</p>
                  </div>
                } @else {
                  <div class="border-t pt-4 space-y-4">
                    <!-- Warning -->
                    <div class="rounded-md bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-3">
                      <p class="text-sm text-amber-800 dark:text-amber-200">{{ credentials()!.warning }}</p>
                    </div>

                    <!-- Connection String -->
                    <app-connection-string
                      label="Connection String"
                      [value]="credentials()!.connectionString"
                    />

                    <!-- Password -->
                    <div class="bg-muted/50 rounded-md p-3">
                      <span class="text-xs text-muted-foreground">Password</span>
                      <div class="flex items-center gap-2">
                        <p class="font-mono text-sm">
                          @if (showPassword()) {
                            {{ credentials()!.password }}
                          } @else {
                            ••••••••••••
                          }
                        </p>
                        <button
                          (click)="togglePassword()"
                          class="p-1 hover:bg-muted rounded"
                          [title]="showPassword() ? 'Hide password' : 'Show password'"
                        >
                          @if (showPassword()) {
                            <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                            </svg>
                          } @else {
                            <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                            </svg>
                          }
                        </button>
                      </div>
                    </div>

                    <button
                      (click)="hideCredentials()"
                      class="text-sm text-muted-foreground hover:text-foreground"
                    >
                      Hide credentials
                    </button>
                  </div>
                }
              </div>
            } @else {
              <p class="text-muted-foreground">Connection info not available</p>
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
  private clusterService = inject(ClusterService);

  loading = signal(true);
  cluster = signal<AdminClusterDetail | null>(null);
  showPassword = signal(false);

  // Credentials state
  credentials = signal<ClusterCredentials | null>(null);
  credentialsLoading = signal(false);

  private clusterId: string = '';

  ngOnInit(): void {
    this.clusterId = this.route.snapshot.paramMap.get('id') || '';
    if (this.clusterId) {
      this.adminService.getAdminCluster(this.clusterId).subscribe({
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

  loadCredentials(): void {
    this.credentialsLoading.set(true);
    this.clusterService.getClusterCredentials(this.clusterId).subscribe({
      next: (creds) => {
        this.credentials.set(creds);
        this.credentialsLoading.set(false);
      },
      error: () => {
        this.credentialsLoading.set(false);
      }
    });
  }

  hideCredentials(): void {
    this.credentials.set(null);
    this.showPassword.set(false);
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
