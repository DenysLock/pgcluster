import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AdminService } from '../../../core/services/admin.service';
import { NotificationService } from '../../../core/services/notification.service';
import { AdminClusterDetail, ClusterCredentials, ClusterNode, ClusterHealth } from '../../../core/models';
import { SpinnerComponent, ConnectionStringComponent, ConfirmDialogComponent } from '../../../shared/components';
import { MetricsCardComponent } from '../../clusters/cluster-detail/metrics-card/metrics-card.component';
import { BackupsCardComponent } from '../../clusters/cluster-detail/backups-card/backups-card.component';
import { ExportsCardComponent } from '../../clusters/cluster-detail/exports-card/exports-card.component';

@Component({
  selector: 'app-admin-cluster-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    SpinnerComponent,
    ConnectionStringComponent,
    ConfirmDialogComponent,
    MetricsCardComponent,
    BackupsCardComponent,
    ExportsCardComponent
  ],
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
          <div class="flex items-center gap-3">
            <span [class]="getStatusClass(cluster()!.status)" class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium">
              {{ cluster()!.status }}
            </span>
            @if (cluster()!.status !== 'deleting' && cluster()!.status !== 'deleted') {
              <button
                (click)="showDeleteDialog.set(true)"
                class="btn-danger h-9 px-4"
                [disabled]="deleting()"
              >
                @if (deleting()) {
                  <span class="spinner w-4 h-4 mr-2"></span>
                  Deleting...
                } @else {
                  <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                  Delete Cluster
                }
              </button>
            }
          </div>
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
                <span class="text-muted-foreground">Regions</span>
                <span class="text-foreground uppercase">{{ allRegions() }}</span>
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
                    <!-- Warning (only show if present) -->
                    @if (credentials()!.warning) {
                      <div class="rounded-md bg-amber-50 border border-amber-200 p-3">
                        <p class="text-sm text-amber-800">{{ credentials()!.warning }}</p>
                      </div>
                    }

                    <!-- Direct Connection String (port 5432) -->
                    <app-connection-string
                      label="Direct Connection (port 5432)"
                      [value]="credentials()!.connectionString"
                    />

                    <!-- Pooled Connection String (port 6432) -->
                    <app-connection-string
                      label="Pooled Connection (port 6432)"
                      [value]="getPooledConnectionString()"
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

          <!-- Cluster Health (only when running) -->
          @if (cluster()!.status === 'running') {
            <div class="card lg:col-span-2">
              <div class="card-header">Cluster Health</div>
              @if (health()) {
                <div class="space-y-4">
                  <!-- Summary stats -->
                  <div class="grid gap-4 md:grid-cols-3">
                    <div class="bg-bg-tertiary border border-border p-4">
                      <p class="text-xs uppercase tracking-wider text-muted-foreground mb-1">Status</p>
                      <div class="flex items-center gap-2">
                        <span [class]="getHealthStatusClass(health()?.overallStatus)" class="inline-block w-2 h-2"></span>
                        <p class="text-lg font-semibold uppercase">{{ health()?.overallStatus || 'unknown' }}</p>
                      </div>
                    </div>
                    <div class="bg-bg-tertiary border border-border p-4">
                      <p class="text-xs uppercase tracking-wider text-muted-foreground mb-1">Leader</p>
                      <p class="text-lg font-semibold font-mono">{{ health()?.patroni?.leader || 'Electing...' }}</p>
                    </div>
                    <div class="bg-bg-tertiary border border-border p-4">
                      <p class="text-xs uppercase tracking-wider text-muted-foreground mb-1">Reachable Nodes</p>
                      <p class="text-lg font-semibold">{{ reachableNodes() }}/{{ totalNodes() }}</p>
                    </div>
                  </div>

                  <!-- Nodes table -->
                  <div class="border border-border">
                    <table class="table w-full">
                      <thead>
                        <tr class="border-b border-border bg-bg-tertiary">
                          <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Node</th>
                          <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Location</th>
                          <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Role</th>
                          <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">IP Address</th>
                          <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">State</th>
                          <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Reachable</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (node of health()?.nodes || []; track node.name) {
                          <tr class="border-b border-border last:border-0 hover:bg-bg-tertiary transition-colors">
                            <td class="px-4 py-3 font-medium text-foreground">{{ node.name }}</td>
                            <td class="px-4 py-3">
                              <span class="flex items-center gap-2">
                                <span class="text-base">{{ node.flag }}</span>
                                <span class="text-sm text-muted-foreground uppercase">{{ node.location }}</span>
                              </span>
                            </td>
                            <td class="px-4 py-3">
                              <span class="badge" [class.badge-success]="node.role === 'leader'" [class.badge-default]="node.role !== 'leader'">
                                {{ node.role }}
                              </span>
                            </td>
                            <td class="px-4 py-3 font-mono text-sm text-gray-300">{{ node.ip }}</td>
                            <td class="px-4 py-3">
                              <span class="badge" [class.badge-success]="node.state === 'running'" [class.badge-warning]="node.state === 'streaming'" [class.badge-default]="node.state !== 'running' && node.state !== 'streaming'">
                                {{ node.state }}
                              </span>
                            </td>
                            <td class="px-4 py-3">
                              @if (node.reachable) {
                                <span class="text-status-running">✓</span>
                              } @else {
                                <span class="text-status-error">✗</span>
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </div>
              } @else {
                <div class="flex items-center justify-center py-8">
                  <span class="spinner w-6 h-6 mr-2"></span>
                  <span>Loading health data...</span>
                </div>
              }
            </div>
          }

          <!-- Metrics Card (only for running clusters) -->
          @if (cluster()!.status === 'running') {
            <div class="lg:col-span-2">
              <app-metrics-card
                [clusterId]="clusterId"
                [isClusterRunning]="true"
                [nodeCount]="cluster()!.node_count"
                [isAdmin]="true"
              />
            </div>
          }

          <!-- Backups Card -->
          <div class="lg:col-span-2">
            <app-backups-card
              [clusterId]="clusterId"
              [clusterSlug]="cluster()!.slug"
              [isClusterRunning]="cluster()!.status === 'running'"
              [clusterNodes]="mapToClusterNodes(cluster()!.nodes || [])"
              [sourceNodeSize]="cluster()!.node_size"
              [sourcePostgresVersion]="cluster()!.postgres_version"
              [isAdmin]="true"
            />
          </div>

          <!-- Exports Card -->
          <div class="lg:col-span-2">
            <app-exports-card
              [clusterId]="clusterId"
              [isClusterRunning]="cluster()!.status === 'running'"
              [isAdmin]="true"
            />
          </div>
        </div>

        <!-- Delete Confirmation Dialog -->
        <app-confirm-dialog
          [open]="showDeleteDialog()"
          title="Delete Cluster"
          [message]="'Are you sure you want to delete cluster ' + cluster()!.name + '? This action cannot be undone and will destroy all data.'"
          confirmText="Delete Cluster"
          variant="destructive"
          (confirm)="deleteCluster()"
          (cancel)="showDeleteDialog.set(false)"
        />
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
  private router = inject(Router);
  private adminService = inject(AdminService);
  private notificationService = inject(NotificationService);

  loading = signal(true);
  cluster = signal<AdminClusterDetail | null>(null);
  showPassword = signal(false);
  showDeleteDialog = signal(false);
  deleting = signal(false);

  // Credentials state
  credentials = signal<ClusterCredentials | null>(null);
  credentialsLoading = signal(false);

  // Health state
  health = signal<ClusterHealth | null>(null);

  clusterId: string = '';

  // Compute all regions from nodes
  allRegions = computed(() => {
    const c = this.cluster();
    if (c?.nodes && c.nodes.length > 0) {
      return c.nodes.map(n => n.location || c.region).join(', ');
    }
    return c?.region || '';
  });

  ngOnInit(): void {
    this.clusterId = this.route.snapshot.paramMap.get('id') || '';
    if (this.clusterId) {
      this.adminService.getAdminCluster(this.clusterId).subscribe({
        next: (cluster) => {
          this.cluster.set(cluster);
          this.loading.set(false);
          // Load health if cluster is running
          if (cluster.status === 'running') {
            this.loadHealth();
          }
        },
        error: () => this.loading.set(false)
      });
    }
  }

  private loadHealth(): void {
    this.adminService.getClusterHealth(this.clusterId).subscribe({
      next: (health) => this.health.set(health),
      error: (err) => console.warn('Failed to load cluster health:', err)
    });
  }

  get totalNodes(): () => number {
    return () => this.health()?.nodes?.length || 0;
  }

  get reachableNodes(): () => number {
    return () => this.health()?.nodes?.filter(n => n.reachable)?.length || 0;
  }

  getHealthStatusClass(status: string | undefined): string {
    switch (status) {
      case 'healthy':
        return 'bg-status-running';
      case 'degraded':
        return 'bg-status-warning';
      case 'unhealthy':
        return 'bg-status-error';
      default:
        return 'bg-gray-500';
    }
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  loadCredentials(): void {
    this.credentialsLoading.set(true);
    this.adminService.getClusterCredentials(this.clusterId).subscribe({
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

  getPooledConnectionString(): string {
    const creds = this.credentials();
    if (!creds) return '';
    // Replace port 5432 with 6432 for pooled connection
    return creds.connectionString.replace(':5432/', ':6432/');
  }

  deleteCluster(): void {
    this.showDeleteDialog.set(false);
    this.deleting.set(true);

    this.adminService.deleteCluster(this.clusterId).subscribe({
      next: () => {
        this.notificationService.success('Cluster deleted successfully');
        this.router.navigate(['/admin/clusters']);
      },
      error: (err) => {
        this.deleting.set(false);
        this.notificationService.error(err.error?.message || 'Failed to delete cluster');
      }
    });
  }

  // Map admin nodes to ClusterNode type for BackupsCard
  mapToClusterNodes(nodes: AdminClusterDetail['nodes']): ClusterNode[] {
    if (!nodes) return [];
    return nodes.map(n => ({
      id: n.id,
      name: n.name,
      role: n.role,
      location: n.location || '',
      publicIp: n.public_ip,
      status: n.status || 'unknown',
      serverType: n.server_type || ''
    }));
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'running':
        return 'bg-green-100 text-green-700 border border-green-200';
      case 'creating':
      case 'provisioning':
      case 'forming':
        return 'bg-amber-100 text-amber-700 border border-amber-200';
      case 'error':
      case 'failed':
        return 'bg-red-100 text-red-700 border border-red-200';
      case 'deleted':
        return 'bg-gray-100 text-gray-500 border border-gray-200';
      default:
        return 'bg-gray-100 text-gray-700 border border-gray-200';
    }
  }

  getRoleClass(role: string): string {
    if (role === 'leader') {
      return 'bg-indigo-100 text-indigo-700 border border-indigo-200';
    }
    return 'bg-slate-100 text-slate-700 border border-slate-200';
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
