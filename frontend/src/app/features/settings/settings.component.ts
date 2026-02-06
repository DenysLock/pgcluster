import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { CardComponent } from '../../shared/components';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, CardComponent],
  template: `
    <div class="max-w-2xl space-y-6">
      <div>
        <h1 class="text-2xl font-bold tracking-tight">Settings</h1>
        <p class="text-muted-foreground">Manage your account settings</p>
      </div>

      <app-card title="Profile" description="Your account information">
        <div class="space-y-4">
          <div class="grid gap-1">
            <label class="text-sm font-medium">Email</label>
            <p class="text-sm text-muted-foreground">{{ userEmail() }}</p>
          </div>
          <div class="grid gap-1">
            <label class="text-sm font-medium">Role</label>
            <p class="text-sm text-muted-foreground capitalize">{{ userRole() }}</p>
          </div>
        </div>
      </app-card>
    </div>
  `
})
export class SettingsComponent {
  constructor(private authService: AuthService) {}

  userEmail = computed(() => this.authService.user()?.email ?? '');
  userRole = computed(() => this.authService.user()?.role ?? 'user');
  memberSince = computed(() => {
    const user = this.authService.user();
    if (!user?.created_at) return '';
    return new Date(user.created_at).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  });
}
