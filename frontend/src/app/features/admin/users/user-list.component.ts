import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../../../core/services/admin.service';
import { User } from '../../../core/models';
import { SpinnerComponent, EmptyStateComponent } from '../../../shared/components';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, SpinnerComponent, EmptyStateComponent],
  template: `
    <div class="space-y-6">
      <!-- Header -->
      <div>
        <h1 class="text-2xl font-bold tracking-tight">Users</h1>
        <p class="text-muted-foreground">All registered users on the platform</p>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else if (users().length === 0) {
        <app-empty-state
          title="No users"
          description="No users have registered yet"
        >
          <svg icon class="w-6 h-6 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
          </svg>
        </app-empty-state>
      } @else {
        <!-- Users Table -->
        <div class="rounded-lg border bg-card">
          <table class="w-full">
            <thead>
              <tr class="border-b bg-muted/50">
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Email</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Role</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Registered</th>
              </tr>
            </thead>
            <tbody>
              @for (user of users(); track user.id) {
                <tr class="border-b last:border-0">
                  <td class="p-4">
                    <div class="flex items-center gap-3">
                      <div class="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
                        <span class="text-sm font-medium text-muted-foreground">
                          {{ user.email.charAt(0).toUpperCase() }}
                        </span>
                      </div>
                      <span class="font-medium">{{ user.email }}</span>
                    </div>
                  </td>
                  <td class="p-4">
                    @if (user.role === 'admin') {
                      <span class="inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium rounded-full bg-purple-50 text-purple-700 border border-purple-200">
                        Admin
                      </span>
                    } @else {
                      <span class="inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium rounded-full bg-gray-50 text-gray-700 border border-gray-200">
                        User
                      </span>
                    }
                  </td>
                  <td class="p-4 text-sm text-muted-foreground">
                    {{ formatDate(user.created_at) }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class UserListComponent implements OnInit {
  private adminService = inject(AdminService);

  loading = signal(true);
  users = this.adminService.users;

  ngOnInit(): void {
    this.loadUsers();
  }

  private loadUsers(): void {
    this.adminService.getUsers().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }
}
