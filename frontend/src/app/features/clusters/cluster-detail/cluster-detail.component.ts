import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { interval, Subscription, switchMap } from 'rxjs';
import { ClusterService } from '../../../core/services/cluster.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Cluster, ClusterCredentials, ClusterHealth } from '../../../core/models';
import { POLLING_INTERVALS } from '../../../core/constants';
import {
  StatusBadgeComponent,
  ConnectionStringComponent,
  ConfirmDialogComponent
} from '../../../shared/components';
import { BackupsCardComponent } from './backups-card/backups-card.component';
import { ExportsCardComponent } from './exports-card/exports-card.component';
import { MetricsCardComponent } from './metrics-card/metrics-card.component';

interface ProvisioningStep {
  id: number;
  key: string;
  label: string;
}

@Component({
  selector: 'app-cluster-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    StatusBadgeComponent,
    ConnectionStringComponent,
    ConfirmDialogComponent,
    BackupsCardComponent,
    ExportsCardComponent,
    MetricsCardComponent
  ],
  template: `
    <div class="space-y-6">
      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <span class="spinner w-8 h-8"></span>
        </div>
      } @else if (cluster()) {
        <!-- Deleted Cluster Message -->
        @if (isDeleted()) {
          <div class="card">
            <div class="text-center py-8">
              <svg class="w-12 h-12 mx-auto mb-4 text-status-stopped" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>

              <!-- Cluster name + badge -->
              <div class="flex items-center justify-center gap-3 mb-4">
                <h2 class="text-xl font-semibold uppercase tracking-wider text-foreground">{{ cluster()?.name }}</h2>
                <app-status-badge status="deleted" />
              </div>

              <!-- Created date -->
              <p class="text-muted-foreground mb-2">Created {{ formatDate(cluster()?.createdAt || '') }}</p>

              <!-- Configuration summary -->
              <p class="text-sm text-muted-foreground">
                {{ cluster()?.nodeCount }}× {{ cluster()?.nodeSize }} nodes • PostgreSQL {{ cluster()?.postgresVersion }} • {{ cluster()?.region }}
              </p>

              <a routerLink="/clusters/new" class="btn-primary mt-8 inline-block">Create New Cluster</a>
            </div>
          </div>
        } @else {
          <!-- Header -->
          <div class="flex items-start justify-between">
            <div>
              <div class="flex items-center gap-3">
                <h1 class="text-xl font-semibold uppercase tracking-wider text-foreground">{{ cluster()?.name }}</h1>
                <app-status-badge [status]="cluster()?.status || 'unknown'" />
              </div>
              <p class="text-muted-foreground text-sm mt-1">Created {{ formatDate(cluster()?.createdAt || '') }}</p>
            </div>
          </div>

          <!-- Provisioning Progress (only during creating/pending) -->
          @if (isProvisioning()) {
          <div class="card">
            <div class="card-header">Provisioning Progress</div>
            <div class="space-y-4">
              @for (step of provisioningSteps(); track step.id) {
                <div class="flex items-center gap-3">
                  <!-- Step indicator -->
                  @if (step.id < currentStep()) {
                    <span class="w-7 h-7 bg-neon-green text-bg-primary flex items-center justify-center text-sm font-semibold">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                      </svg>
                    </span>
                  } @else if (step.id === currentStep()) {
                    <span class="w-7 h-7 bg-status-warning text-bg-primary flex items-center justify-center text-sm font-semibold animate-pulse">
                      {{ step.id }}
                    </span>
                  } @else {
                    <span class="w-7 h-7 bg-bg-tertiary border border-border text-muted-foreground flex items-center justify-center text-sm font-medium">
                      {{ step.id }}
                    </span>
                  }
                  <!-- Step label -->
                  <span [class]="step.id <= currentStep() ? 'text-foreground' : 'text-muted-foreground'">
                    {{ step.label }}
                  </span>
                </div>
              }
            </div>
          </div>
        }

          <!-- Cluster Health (only when running) -->
          @if (isRunning()) {
            <div class="card">
              <div class="card-header">Cluster Health</div>
              @if (health()) {
            <div class="space-y-4">
              <!-- Summary -->
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

              <!-- Nodes Table -->
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
                            <span class="text-base">{{ node.flag || '🌍' }}</span>
                            <span class="text-sm text-muted-foreground uppercase">{{ node.location || '-' }}</span>
                          </span>
                        </td>
                        <td class="px-4 py-3">
                          <span [class]="getRoleClass(node.role)" class="inline-flex items-center px-2 py-0.5 text-xs font-semibold uppercase">
                            {{ node.role || 'unknown' }}
                          </span>
                        </td>
                        <td class="px-4 py-3 font-mono text-sm text-gray-300">{{ node.ip }}</td>
                        <td class="px-4 py-3">
                          <span [class]="getStateClass(node.state)" class="inline-flex items-center px-2 py-0.5 text-xs font-semibold uppercase">
                            {{ node.state || 'unknown' }}
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
                <div class="flex items-center gap-3 text-muted-foreground py-4">
                  <span class="spinner w-4 h-4"></span>
                  <span>Loading health data...</span>
                </div>
              }
            </div>
          }

          <!-- Connection Details (only when running) -->
        @if (isRunning() && cluster()?.connection) {
          <div class="card">
            <div class="card-header">Connection Details</div>
            <div class="space-y-6">
              <!-- Basic connection info (always visible) -->
              <div class="grid gap-4 md:grid-cols-2">
                <div class="bg-bg-tertiary border border-border p-4">
                  <p class="text-xs uppercase tracking-wider text-muted-foreground mb-1">Host</p>
                  <p class="font-mono text-sm text-foreground">{{ cluster()?.connection?.hostname }}</p>
                </div>
                <div class="bg-bg-tertiary border border-border p-4">
                  <p class="text-xs uppercase tracking-wider text-muted-foreground mb-1">Ports</p>
                  <p class="font-mono text-sm text-foreground">6432 <span class="text-muted-foreground">(pooled)</span> / 5432 <span class="text-muted-foreground">(direct)</span></p>
                </div>
              </div>

              <!-- Credentials section -->
              @if (!credentials()) {
                <div class="border-t border-border pt-4">
                  <button
                    (click)="loadCredentials()"
                    [disabled]="credentialsLoading()"
                    class="btn-secondary"
                  >
                    @if (credentialsLoading()) {
                      <span class="spinner w-4 h-4 mr-2"></span>
                      Loading...
                    } @else {
                      <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                      </svg>
                      Show Credentials
                    }
                  </button>
                  <p class="text-xs text-muted-foreground mt-2">Click to reveal password and connection strings. Access is logged.</p>
                </div>
              } @else {
                <div class="border-t border-border pt-4 space-y-4">
                  <!-- Warning banner -->
                  <div class="bg-status-warning/10 border border-status-warning p-3">
                    <div class="flex items-start gap-2">
                      <svg class="w-5 h-5 text-status-warning shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                      </svg>
                      <p class="text-sm text-status-warning">{{ credentials()?.warning }}</p>
                    </div>
                  </div>

                  <!-- Pooled Connection (Recommended) -->
                  <div class="space-y-2">
                    <div class="flex items-center gap-2">
                      <span class="inline-flex items-center px-2 py-0.5 text-xs font-semibold uppercase border border-neon-green text-neon-green">
                        Pooled (Recommended)
                      </span>
                      <span class="text-sm text-muted-foreground">Port 6432</span>
                    </div>
                    <app-connection-string
                      label="Pooled Connection String"
                      [value]="credentials()?.pooledConnectionString || ''"
                      description="Connection pooled via PgBouncer. Recommended for web applications."
                    />
                  </div>

                  <!-- Direct Connection -->
                  <div class="space-y-2">
                    <div class="flex items-center gap-2">
                      <span class="inline-flex items-center px-2 py-0.5 text-xs font-semibold uppercase border border-neon-cyan text-neon-cyan">
                        Direct
                      </span>
                      <span class="text-sm text-muted-foreground">Port 5432</span>
                    </div>
                    <app-connection-string
                      label="Direct Connection String"
                      [value]="credentials()?.connectionString || ''"
                      description="Direct PostgreSQL connection. Use for admin tasks and migrations."
                    />
                  </div>

                  <!-- Usage Guide -->
                  <div class="bg-bg-tertiary border border-border p-4">
                    <p class="text-sm font-semibold mb-2 text-foreground">When to use each connection</p>
                    <div class="grid gap-2 text-sm text-gray-300">
                      <div class="flex items-start gap-2">
                        <span class="text-neon-green font-mono shrink-0">6432</span>
                        <span>Web apps, APIs, serverless functions, high concurrency workloads</span>
                      </div>
                      <div class="flex items-start gap-2">
                        <span class="text-neon-cyan font-mono shrink-0">5432</span>
                        <span>Migrations, LISTEN/NOTIFY, prepared statements, admin tools (pgAdmin, psql)</span>
                      </div>
                    </div>
                  </div>

                  <!-- Hide credentials button -->
                  <button
                    (click)="hideCredentials()"
                    class="text-sm text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Hide credentials
                  </button>
                </div>
              }
            </div>
          </div>
        }

          <!-- Metrics Card (visible when running) -->
          @if (isRunning()) {
            <app-metrics-card
              [clusterId]="clusterId"
              [isClusterRunning]="isRunning()"
              [nodeCount]="cluster()?.nodeCount || 1"
            />
          }

          <!-- Backups Card (visible when running or has backups) -->
          @if (isRunning()) {
            <app-backups-card
              [clusterId]="clusterId"
              [clusterSlug]="cluster()?.slug || ''"
              [isClusterRunning]="isRunning()"
              [clusterNodes]="cluster()?.nodes || []"
            />
          }

          <!-- Exports Card (visible when running) -->
          @if (isRunning()) {
            <app-exports-card
              [clusterId]="clusterId"
              [isClusterRunning]="isRunning()"
            />
          }

          <!-- Danger Zone -->
          <div class="card border-status-error/30">
            <div class="card-header text-status-error">Danger Zone</div>
            <div class="flex items-center justify-between">
              <div>
                <p class="font-semibold text-foreground">Delete this cluster</p>
                <p class="text-sm text-muted-foreground">Once deleted, this cluster cannot be recovered.</p>
              </div>
              <button
                (click)="showDeleteDialog.set(true)"
                [disabled]="deleting()"
                class="btn-danger"
              >
                @if (deleting()) {
                  <span class="spinner w-4 h-4 mr-2 border-status-error"></span>
                }
                Delete Cluster
              </button>
            </div>
          </div>

          <!-- Delete Confirmation Dialog -->
          <app-confirm-dialog
            [open]="showDeleteDialog()"
            title="Delete Cluster"
            [message]="'Are you sure you want to delete ' + cluster()?.name + '? This action cannot be undone and all data will be permanently lost.'"
            confirmText="Delete"
            variant="destructive"
            (confirm)="deleteCluster()"
            (cancel)="showDeleteDialog.set(false)"
          />
        }
      } @else {
        <div class="text-center py-12">
          <p class="text-muted-foreground">Cluster not found</p>
          <a routerLink="/" class="text-neon-green hover:underline">Back to Clusters</a>
        </div>
      }
    </div>
  `
})
export class ClusterDetailComponent implements OnInit, OnDestroy {
  loading = signal(true);
  deleting = signal(false);
  showDeleteDialog = signal(false);
  cluster = signal<Cluster | null>(null);
  health = signal<ClusterHealth | null>(null);

  // Credentials state
  credentials = signal<ClusterCredentials | null>(null);
  credentialsLoading = signal(false);

  clusterId: string = '';
  private pollingSubscription?: Subscription;
  private routeSubscription?: Subscription;

  // Provisioning steps - computed based on node count
  provisioningSteps = computed<ProvisioningStep[]>(() => {
    const nodeCount = this.cluster()?.nodeCount || 3;
    if (nodeCount === 1) {
      // Single node: rename "Electing leader" to "Starting PostgreSQL"
      return [
        { id: 1, key: 'creating_servers', label: 'Creating server' },
        { id: 2, key: 'waiting_ssh', label: 'Waiting for SSH' },
        { id: 3, key: 'building_config', label: 'Building configuration' },
        { id: 4, key: 'starting_containers', label: 'Starting containers' },
        { id: 5, key: 'starting_postgres', label: 'Starting PostgreSQL' },
        { id: 6, key: 'creating_dns', label: 'Creating DNS record' },
      ];
    }
    // HA cluster: all steps
    return [
      { id: 1, key: 'creating_servers', label: 'Creating servers' },
      { id: 2, key: 'waiting_ssh', label: 'Waiting for SSH' },
      { id: 3, key: 'building_config', label: 'Building configuration' },
      { id: 4, key: 'starting_containers', label: 'Starting containers' },
      { id: 5, key: 'electing_leader', label: 'Electing Patroni leader' },
      { id: 6, key: 'creating_dns', label: 'Creating DNS record' },
    ];
  });

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private clusterService: ClusterService,
    private notificationService: NotificationService
  ) {}

  // Computed signals
  isProvisioning = computed(() => {
    const status = this.cluster()?.status;
    return status === 'pending' || status === 'creating';
  });

  isRunning = computed(() => {
    return this.cluster()?.status === 'running';
  });

  isDeleted = computed(() => {
    const status = this.cluster()?.status;
    return status === 'deleting' || status === 'deleted';
  });

  isSingleNode = computed(() => {
    return (this.cluster()?.nodeCount || 3) === 1;
  });

  currentStep = computed(() => {
    return this.cluster()?.provisioningProgress || 0;
  });

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
        return 'bg-status-stopped';
    }
  }

  getRoleClass(role: string | null): string {
    switch (role) {
      case 'leader':
        return 'border border-neon-green text-neon-green';
      case 'replica':
        return 'border border-neon-cyan text-neon-cyan';
      default:
        return 'border border-border text-muted-foreground';
    }
  }

  getStateClass(state: string | null): string {
    switch (state) {
      case 'running':
        return 'border border-status-running text-status-running';
      case 'streaming':
        return 'border border-neon-cyan text-neon-cyan';
      default:
        return 'border border-border text-muted-foreground';
    }
  }

  ngOnInit(): void {
    // Subscribe to route parameter changes to handle navigation between clusters
    this.routeSubscription = this.route.paramMap.subscribe(params => {
      const newId = params.get('id') || '';
      if (newId !== this.clusterId) {
        // Reset state when switching clusters
        this.pollingSubscription?.unsubscribe();
        this.clusterId = newId;
        this.loading.set(true);
        this.cluster.set(null);
        this.health.set(null);
        this.credentials.set(null);
        this.loadCluster();
        this.startPolling();
      }
    });
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
    this.routeSubscription?.unsubscribe();
  }

  private loadCluster(): void {
    this.clusterService.getCluster(this.clusterId).subscribe({
      next: (cluster) => {
        this.cluster.set(cluster);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  private startPolling(): void {
    // Initial health load
    this.loadHealth();

    // Poll for both cluster status and health
    this.pollingSubscription = interval(POLLING_INTERVALS.CLUSTER_STATUS).subscribe(() => {
      // Always refresh cluster status (for progress updates)
      this.clusterService.getCluster(this.clusterId).subscribe({
        next: (cluster) => this.cluster.set(cluster),
        error: (err) => console.warn('Failed to refresh cluster status:', err)
      });

      // Only fetch health if cluster is running
      if (this.isRunning()) {
        this.loadHealth();
      }
    });
  }

  private loadHealth(): void {
    this.clusterService.getClusterHealth(this.clusterId).subscribe({
      next: (health) => this.health.set(health),
      error: (err) => console.warn('Failed to load cluster health:', err)
    });
  }

  deleteCluster(): void {
    this.showDeleteDialog.set(false);
    this.deleting.set(true);

    this.clusterService.deleteCluster(this.clusterId).subscribe({
      next: () => {
        this.notificationService.success('Cluster deleted successfully');
        this.router.navigate(['/clusters']);
      },
      error: (err) => {
        this.deleting.set(false);
        this.notificationService.error(err.error?.message || 'Failed to delete cluster');
      }
    });
  }

  formatDate(dateString: string): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
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
  }
}
