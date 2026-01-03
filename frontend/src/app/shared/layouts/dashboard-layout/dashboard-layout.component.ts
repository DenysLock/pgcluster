import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, RouterOutlet],
  template: `
    <div class="min-h-screen bg-background">
      <!-- Top Navigation -->
      <header class="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div class="container flex h-14 items-center">
          <div class="mr-4 flex">
            <a routerLink="/dashboard" class="mr-6 flex items-center space-x-2">
              <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                <path d="M2 17l10 5 10-5"/>
                <path d="M2 12l10 5 10-5"/>
              </svg>
              <span class="font-bold">DBaaS</span>
            </a>
          </div>

          <nav class="flex items-center space-x-6 text-sm font-medium">
            <a routerLink="/dashboard" routerLinkActive="text-foreground" [routerLinkActiveOptions]="{exact: true}"
               class="transition-colors hover:text-foreground/80 text-foreground/60">
              Dashboard
            </a>
            <a routerLink="/clusters" routerLinkActive="text-foreground"
               class="transition-colors hover:text-foreground/80 text-foreground/60">
              Clusters
            </a>
            @if (isAdmin()) {
              <span class="text-foreground/30">|</span>
              <a routerLink="/admin" routerLinkActive="text-foreground" [routerLinkActiveOptions]="{exact: true}"
                 class="transition-colors hover:text-foreground/80 text-foreground/60">
                Admin
              </a>
              <a routerLink="/admin/clusters" routerLinkActive="text-foreground"
                 class="transition-colors hover:text-foreground/80 text-foreground/60">
                Clusters
              </a>
              <a routerLink="/admin/users" routerLinkActive="text-foreground"
                 class="transition-colors hover:text-foreground/80 text-foreground/60">
                Users
              </a>
            }
          </nav>

          <div class="flex flex-1 items-center justify-end space-x-4">
            <nav class="flex items-center space-x-2">
              <span class="text-sm text-muted-foreground">{{ userEmail() }}</span>
              <button
                (click)="logout()"
                class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 hover:bg-accent hover:text-accent-foreground h-9 px-3"
              >
                Logout
              </button>
            </nav>
          </div>
        </div>
      </header>

      <!-- Main Content -->
      <main class="container py-6">
        <router-outlet />
      </main>
    </div>
  `
})
export class DashboardLayoutComponent {
  private authService = inject(AuthService);

  isAdmin = this.authService.isAdmin;
  userEmail = computed(() => this.authService.user()?.email ?? '');

  logout(): void {
    this.authService.logout();
  }
}
