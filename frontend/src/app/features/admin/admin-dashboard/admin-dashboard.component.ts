import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminService } from '../../../core/services/admin.service';
import { PlatformStats } from '../../../core/models';
import { SpinnerComponent } from '../../../shared/components';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, SpinnerComponent],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-bold tracking-tight">Admin Dashboard</h1>
        <p class="text-muted-foreground">Platform overview and management</p>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else {
        <!-- Stats Cards -->
        <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <div class="rounded-lg border bg-card text-card-foreground shadow-sm p-6">
            <div class="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 class="text-sm font-medium text-muted-foreground">Total Clusters</h3>
              <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
              </svg>
            </div>
            <div class="text-2xl font-bold">{{ stats()?.total_clusters || 0 }}</div>
            <p class="text-xs text-muted-foreground mt-1">{{ stats()?.running_clusters || 0 }} running</p>
          </div>

          <div class="rounded-lg border bg-card text-card-foreground shadow-sm p-6">
            <div class="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 class="text-sm font-medium text-muted-foreground">Total Users</h3>
              <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </div>
            <div class="text-2xl font-bold">{{ stats()?.total_users || 0 }}</div>
          </div>

          <div class="rounded-lg border bg-card text-card-foreground shadow-sm p-6">
            <div class="flex flex-row items-center justify-between space-y-0 pb-2">
              <h3 class="text-sm font-medium text-muted-foreground">Cluster Health</h3>
              <svg class="w-4 h-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div class="text-2xl font-bold">{{ healthPercentage() }}%</div>
            <p class="text-xs text-muted-foreground mt-1">clusters running</p>
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="grid gap-4 md:grid-cols-2">
          <a
            routerLink="/admin/clusters"
            class="rounded-lg border bg-card p-6 hover:bg-muted/50 transition-colors"
          >
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-lg bg-muted flex items-center justify-center">
                <svg class="w-6 h-6 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
                </svg>
              </div>
              <div>
                <h3 class="font-semibold">View All Clusters</h3>
                <p class="text-sm text-muted-foreground">See all clusters across users</p>
              </div>
            </div>
          </a>

          <a
            routerLink="/admin/users"
            class="rounded-lg border bg-card p-6 hover:bg-muted/50 transition-colors"
          >
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-lg bg-muted flex items-center justify-center">
                <svg class="w-6 h-6 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                </svg>
              </div>
              <div>
                <h3 class="font-semibold">View Users</h3>
                <p class="text-sm text-muted-foreground">See all registered users</p>
              </div>
            </div>
          </a>
        </div>
      }
    </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  loading = signal(true);
  stats = signal<PlatformStats | null>(null);

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadStats();
  }

  healthPercentage(): number {
    const s = this.stats();
    if (!s || s.total_clusters === 0) return 0;
    return Math.round((s.running_clusters / s.total_clusters) * 100);
  }

  private loadStats(): void {
    this.adminService.getPlatformStats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}
