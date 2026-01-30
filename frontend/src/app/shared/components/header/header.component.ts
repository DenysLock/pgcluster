import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ClusterService } from '../../../core/services/cluster.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <header class="h-14 bg-white border-b border-border flex items-center justify-between px-6">
      <!-- Left: Logo + Status Summary -->
      <div class="flex items-center gap-6">
        <a routerLink="/" class="text-primary font-bold text-lg tracking-wider hover:text-blue-700">
          PGCLUSTER
        </a>
        <!-- Status Summary -->
        <div class="flex items-center gap-4">
          @if (runningCount() > 0) {
            <div class="flex items-center gap-2">
              <span class="status-dot status-dot-running"></span>
              <span class="text-xs font-semibold text-status-running">{{ runningCount() }} RUNNING</span>
            </div>
          }
          @if (warningCount() > 0) {
            <div class="flex items-center gap-2">
              <span class="status-dot status-dot-warning"></span>
              <span class="text-xs font-semibold text-status-warning">{{ warningCount() }} WARNING</span>
            </div>
          }
          @if (errorCount() > 0) {
            <div class="flex items-center gap-2">
              <span class="status-dot status-dot-error"></span>
              <span class="text-xs font-semibold text-status-error">{{ errorCount() }} ERROR</span>
            </div>
          }
        </div>
      </div>

      <!-- Right: User Menu -->
      <div class="relative">
        <button
          (click)="toggleMenu()"
          class="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <span class="text-sm">{{ userEmail() }}</span>
          <svg
            class="w-4 h-4 transition-transform"
            [class.rotate-180]="menuOpen"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </button>

        @if (menuOpen) {
          <div class="absolute right-0 top-full mt-1 w-48 bg-white border border-border rounded shadow-lg z-50">
            <a
              routerLink="/settings"
              (click)="menuOpen = false"
              class="block px-4 py-3 text-sm text-foreground hover:bg-bg-tertiary transition-colors"
            >
              Settings
            </a>
            <button
              (click)="logout()"
              class="w-full text-left px-4 py-3 text-sm text-status-error hover:bg-bg-tertiary transition-colors"
            >
              Logout
            </button>
          </div>
        }
      </div>
    </header>

    <!-- Click outside to close menu -->
    @if (menuOpen) {
      <div class="fixed inset-0 z-40" (click)="menuOpen = false"></div>
    }
  `,
})
export class HeaderComponent {
  private authService = inject(AuthService);
  private clusterService = inject(ClusterService);
  private router = inject(Router);

  menuOpen = false;

  userEmail = computed(() => this.authService.user()?.email || 'User');

  clusters = this.clusterService.clusters;

  runningCount = computed(() =>
    this.clusters().filter(c => c.status === 'running').length
  );

  warningCount = computed(() =>
    this.clusters().filter(c =>
      ['pending', 'creating', 'provisioning', 'forming', 'degraded'].includes(c.status)
    ).length
  );

  errorCount = computed(() =>
    this.clusters().filter(c => c.status === 'error').length
  );

  toggleMenu() {
    this.menuOpen = !this.menuOpen;
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
