import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClusterService } from '../../core/services/cluster.service';
import { AuthService } from '../../core/services/auth.service';
import { Cluster } from '../../core/models';
import { CardComponent, StatusBadgeComponent, SpinnerComponent, EmptyStateComponent } from '../../shared/components';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, CardComponent, StatusBadgeComponent, SpinnerComponent, EmptyStateComponent],
  template: `
    <div class="space-y-6">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-lg font-bold">Dashboard</h1>
          <p class="text-sm text-muted-foreground">Welcome back, {{ userEmail() }}</p>
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

      <!-- Stats Cards -->
      <div class="grid gap-4 md:grid-cols-3">
        <div class="rounded-lg border bg-card text-card-foreground shadow-sm p-6">
          <div class="flex flex-row items-center justify-between space-y-0 pb-2">
            <h3 class="text-sm font-medium text-muted-foreground">Total Clusters</h3>
            <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
            </svg>
          </div>
          <div class="text-lg font-bold">{{ totalClusters() }}</div>
        </div>

        <div class="rounded-lg border bg-card text-card-foreground shadow-sm p-6">
          <div class="flex flex-row items-center justify-between space-y-0 pb-2">
            <h3 class="text-sm font-medium text-muted-foreground">Running</h3>
            <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div class="text-lg font-bold text-emerald-600">{{ runningClusters() }}</div>
        </div>

        <div class="rounded-lg border bg-card text-card-foreground shadow-sm p-6">
          <div class="flex flex-row items-center justify-between space-y-0 pb-2">
            <h3 class="text-sm font-medium text-muted-foreground">Issues</h3>
            <svg class="w-4 h-4 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div class="text-lg font-bold" [class.text-amber-600]="issuesClusters() > 0">{{ issuesClusters() }}</div>
        </div>
      </div>

      <!-- Clusters List -->
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold">Your Clusters</h2>
          <a routerLink="/clusters" class="text-sm text-primary hover:underline">View all</a>
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
          <div class="rounded-lg border bg-card">
            <div class="divide-y">
              @for (cluster of clusters().slice(0, 5); track cluster.id) {
                <a
                  [routerLink]="['/clusters', cluster.id]"
                  class="flex items-center justify-between p-4 hover:bg-muted/50 transition-colors"
                >
                  <div class="flex items-center gap-4">
                    <div class="w-10 h-10 rounded-full bg-muted flex items-center justify-center">
                      <svg class="w-5 h-5 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
                      </svg>
                    </div>
                    <div>
                      <p class="font-medium">{{ cluster.name }}</p>
                      <p class="text-sm text-muted-foreground">{{ cluster.slug }}.db.pgcluster.com</p>
                    </div>
                  </div>
                  <div class="flex items-center gap-4">
                    <app-status-badge [status]="cluster.status" size="sm" />
                    <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                    </svg>
                  </div>
                </a>
              }
            </div>
          </div>
        }
      </div>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  private clusterService = inject(ClusterService);
  private authService = inject(AuthService);

  loading = signal(true);
  clusters = this.clusterService.clusters;
  userEmail = computed(() => this.authService.user()?.email ?? '');

  totalClusters = computed(() => this.clusters().length);
  runningClusters = computed(() => this.clusters().filter(c => c.status === 'running').length);
  issuesClusters = computed(() => this.clusters().filter(c => ['error', 'degraded'].includes(c.status)).length);

  ngOnInit(): void {
    this.loadClusters();
  }

  private loadClusters(): void {
    this.clusterService.getClusters().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }
}
