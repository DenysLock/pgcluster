import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { interval, Subscription, switchMap } from 'rxjs';
import { ClusterService } from '../../../core/services/cluster.service';
import { Cluster, ClusterCredentials, ClusterHealth } from '../../../core/models';
import {
  StatusBadgeComponent,
  SpinnerComponent,
  ConnectionStringComponent,
  ConfirmDialogComponent,
  CardComponent
} from '../../../shared/components';

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
    SpinnerComponent,
    ConnectionStringComponent,
    ConfirmDialogComponent,
    CardComponent
  ],
  template: `
    <div class="space-y-6">
      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else if (cluster()) {
        <!-- Header -->
        <div class="flex items-start justify-between">
          <div>
            <a routerLink="/clusters" class="text-sm text-muted-foreground hover:text-foreground inline-flex items-center mb-4">
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
              Back to Clusters
            </a>
            <div class="flex items-center gap-3">
              <h1 class="text-2xl font-bold tracking-tight">{{ cluster()?.name }}</h1>
              <app-status-badge [status]="cluster()?.status || 'unknown'" />
            </div>
            <p class="text-muted-foreground mt-1">Created {{ formatDate(cluster()?.createdAt || '') }}</p>
          </div>
        </div>

        <!-- Provisioning Progress (only during creating/pending) -->
        @if (isProvisioning()) {
          <app-card title="Provisioning Progress" description="Your cluster is being created">
            <div class="space-y-4">
              @for (step of provisioningSteps; track step.id) {
                <div class="flex items-center gap-3">
                  <!-- Step indicator -->
                  @if (step.id < currentStep()) {
                    <span class="w-7 h-7 rounded-full bg-emerald-500 text-white flex items-center justify-center text-sm font-medium">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                      </svg>
                    </span>
                  } @else if (step.id === currentStep()) {
                    <span class="w-7 h-7 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-medium animate-pulse">
                      {{ step.id }}
                    </span>
                  } @else {
                    <span class="w-7 h-7 rounded-full bg-muted text-muted-foreground flex items-center justify-center text-sm font-medium">
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
          </app-card>
        }

        <!-- Cluster Health (always visible) -->
        <app-card title="Cluster Health" description="Real-time status of your PostgreSQL cluster">
          @if (!isRunning()) {
            <div class="flex items-center gap-3 text-muted-foreground py-4">
              <app-spinner size="sm" />
              <span>Waiting for cluster to be ready...</span>
            </div>
          } @else if (health()) {
            <div class="space-y-4">
              <!-- Summary -->
              <div class="grid gap-4 md:grid-cols-3">
                <div class="rounded-lg bg-muted/50 p-4">
                  <p class="text-sm text-muted-foreground mb-1">Status</p>
                  <div class="flex items-center gap-2">
                    <span [class]="getHealthStatusClass(health()?.overallStatus)" class="inline-block w-2 h-2 rounded-full"></span>
                    <p class="text-lg font-semibold capitalize">{{ health()?.overallStatus || 'unknown' }}</p>
                  </div>
                </div>
                <div class="rounded-lg bg-muted/50 p-4">
                  <p class="text-sm text-muted-foreground mb-1">Leader</p>
                  <p class="text-lg font-semibold font-mono">{{ health()?.patroni?.leader || 'Electing...' }}</p>
                </div>
                <div class="rounded-lg bg-muted/50 p-4">
                  <p class="text-sm text-muted-foreground mb-1">Reachable Nodes</p>
                  <p class="text-lg font-semibold">{{ reachableNodes() }}/{{ totalNodes() }}</p>
                </div>
              </div>

              <!-- Nodes Table -->
              <div class="rounded-lg border">
                <table class="w-full">
                  <thead>
                    <tr class="border-b bg-muted/50">
                      <th class="h-10 px-4 text-left align-middle font-medium text-muted-foreground text-sm">Node</th>
                      <th class="h-10 px-4 text-left align-middle font-medium text-muted-foreground text-sm">Role</th>
                      <th class="h-10 px-4 text-left align-middle font-medium text-muted-foreground text-sm">IP Address</th>
                      <th class="h-10 px-4 text-left align-middle font-medium text-muted-foreground text-sm">State</th>
                      <th class="h-10 px-4 text-left align-middle font-medium text-muted-foreground text-sm">Reachable</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (node of health()?.nodes || []; track node.name) {
                      <tr class="border-b last:border-0">
                        <td class="p-4 font-medium">{{ node.name }}</td>
                        <td class="p-4">
                          <span [class]="getRoleClass(node.role)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                            {{ node.role || 'unknown' }}
                          </span>
                        </td>
                        <td class="p-4 font-mono text-sm">{{ node.ip }}</td>
                        <td class="p-4">
                          <span [class]="getStateClass(node.state)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                            {{ node.state || 'unknown' }}
                          </span>
                        </td>
                        <td class="p-4">
                          @if (node.reachable) {
                            <span class="text-emerald-500">✓</span>
                          } @else {
                            <span class="text-red-500">✗</span>
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
              <app-spinner size="sm" />
              <span>Loading health data...</span>
            </div>
          }
        </app-card>

        <!-- Connection Details (only when running) -->
        @if (isRunning() && cluster()?.connection) {
          <app-card title="Connection Details" description="Use these credentials to connect to your database">
            <div class="space-y-6">
              <!-- Basic connection info (always visible) -->
              <div class="grid gap-4 md:grid-cols-2">
                <div class="rounded-lg bg-muted/50 p-4">
                  <p class="text-sm text-muted-foreground mb-1">Host</p>
                  <p class="font-mono text-sm">{{ cluster()?.connection?.hostname }}</p>
                </div>
                <div class="rounded-lg bg-muted/50 p-4">
                  <p class="text-sm text-muted-foreground mb-1">Ports</p>
                  <p class="font-mono text-sm">6432 <span class="text-muted-foreground">(pooled)</span> / 5432 <span class="text-muted-foreground">(direct)</span></p>
                </div>
              </div>

              <!-- Credentials section -->
              @if (!credentials()) {
                <div class="border-t pt-4">
                  <button
                    (click)="loadCredentials()"
                    [disabled]="credentialsLoading()"
                    class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-10 px-4 py-2"
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
                  <p class="text-xs text-muted-foreground mt-2">Click to reveal password and connection strings. Access is logged.</p>
                </div>
              } @else {
                <div class="border-t pt-4 space-y-4">
                  <!-- Warning banner -->
                  <div class="rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-3">
                    <div class="flex items-start gap-2">
                      <svg class="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                      </svg>
                      <p class="text-sm text-amber-800 dark:text-amber-200">{{ credentials()?.warning }}</p>
                    </div>
                  </div>

                  <!-- Pooled Connection (Recommended) -->
                  <div class="space-y-2">
                    <div class="flex items-center gap-2">
                      <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400">
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
                      <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400">
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
                  <div class="rounded-lg bg-muted/30 border p-4">
                    <p class="text-sm font-medium mb-2">When to use each connection</p>
                    <div class="grid gap-2 text-sm text-muted-foreground">
                      <div class="flex items-start gap-2">
                        <span class="text-emerald-600 dark:text-emerald-400 font-mono shrink-0">6432</span>
                        <span>Web apps, APIs, serverless functions, high concurrency workloads</span>
                      </div>
                      <div class="flex items-start gap-2">
                        <span class="text-blue-600 dark:text-blue-400 font-mono shrink-0">5432</span>
                        <span>Migrations, LISTEN/NOTIFY, prepared statements, admin tools (pgAdmin, psql)</span>
                      </div>
                    </div>
                  </div>

                  <!-- Hide credentials button -->
                  <button
                    (click)="hideCredentials()"
                    class="text-sm text-muted-foreground hover:text-foreground"
                  >
                    Hide credentials
                  </button>
                </div>
              }
            </div>
          </app-card>
        }

        <!-- Danger Zone -->
        <app-card title="Danger Zone" variant="danger">
          <div class="flex items-center justify-between">
            <div>
              <p class="font-medium">Delete this cluster</p>
              <p class="text-sm text-muted-foreground">Once deleted, this cluster cannot be recovered.</p>
            </div>
            <button
              (click)="showDeleteDialog.set(true)"
              [disabled]="deleting()"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-destructive text-destructive-foreground hover:bg-destructive/90 h-10 px-4 py-2"
            >
              @if (deleting()) {
                <app-spinner size="sm" class="mr-2" />
              }
              Delete Cluster
            </button>
          </div>
        </app-card>

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
      } @else {
        <div class="text-center py-12">
          <p class="text-muted-foreground">Cluster not found</p>
          <a routerLink="/clusters" class="text-primary hover:underline">Back to Clusters</a>
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

  private clusterId: string = '';
  private pollingSubscription?: Subscription;

  // Provisioning steps configuration
  readonly provisioningSteps: ProvisioningStep[] = [
    { id: 1, key: 'creating_servers', label: 'Creating servers' },
    { id: 2, key: 'waiting_ssh', label: 'Waiting for SSH' },
    { id: 3, key: 'building_config', label: 'Building configuration' },
    { id: 4, key: 'starting_containers', label: 'Starting containers' },
    { id: 5, key: 'electing_leader', label: 'Electing Patroni leader' },
    { id: 6, key: 'creating_dns', label: 'Creating DNS record' },
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private clusterService: ClusterService
  ) {}

  // Computed signals
  isProvisioning = computed(() => {
    const status = this.cluster()?.status;
    return status === 'pending' || status === 'creating';
  });

  isRunning = computed(() => {
    return this.cluster()?.status === 'running';
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
        return 'bg-emerald-500';
      case 'degraded':
        return 'bg-amber-500';
      case 'unhealthy':
        return 'bg-red-500';
      default:
        return 'bg-gray-400';
    }
  }

  getRoleClass(role: string | null): string {
    switch (role) {
      case 'leader':
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400';
      case 'replica':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400';
    }
  }

  getStateClass(state: string | null): string {
    switch (state) {
      case 'running':
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400';
      case 'streaming':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400';
    }
  }

  ngOnInit(): void {
    this.clusterId = this.route.snapshot.paramMap.get('id') || '';
    this.loadCluster();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
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

    // Poll every 10 seconds for both cluster status and health
    this.pollingSubscription = interval(10000).subscribe(() => {
      // Always refresh cluster status (for progress updates)
      this.clusterService.getCluster(this.clusterId).subscribe({
        next: (cluster) => this.cluster.set(cluster),
        error: () => {}
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
      error: () => {}
    });
  }

  deleteCluster(): void {
    this.showDeleteDialog.set(false);
    this.deleting.set(true);

    this.clusterService.deleteCluster(this.clusterId).subscribe({
      next: () => {
        this.router.navigate(['/clusters']);
      },
      error: () => {
        this.deleting.set(false);
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
