import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { User } from '../../../core/models';
import { SpinnerComponent, EmptyStateComponent, ConfirmDialogComponent } from '../../../shared/components';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, SpinnerComponent, EmptyStateComponent, ConfirmDialogComponent],
  template: `
    <div class="space-y-6">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-lg font-bold">Users</h1>
          <p class="text-muted-foreground">Manage platform users</p>
        </div>
        <button (click)="openCreateDialog()" class="btn-primary">
          Create User
        </button>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else if (users().length === 0) {
        <app-empty-state
          title="No users"
          description="No users have been created yet"
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
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Status</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Role</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Registered</th>
                <th class="h-12 px-4 text-right align-middle font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (user of users(); track user.id) {
                <tr class="border-b last:border-0 hover:bg-muted/25">
                  <td class="p-4">
                    <a [routerLink]="['/admin/users', user.id]" class="flex items-center gap-3 hover:underline">
                      <div class="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
                        <span class="text-sm font-medium text-muted-foreground">
                          {{ user.email.charAt(0).toUpperCase() }}
                        </span>
                      </div>
                      <span class="font-medium">{{ user.email }}</span>
                    </a>
                  </td>
                  <td class="p-4">
                    @if (user.active) {
                      <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-green-100 text-green-700 border border-green-200">
                        Active
                      </span>
                    } @else {
                      <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-red-100 text-red-700 border border-red-200">
                        Disabled
                      </span>
                    }
                  </td>
                  <td class="p-4">
                    @if (user.role === 'admin') {
                      <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-purple-100 text-purple-700 border border-purple-200">
                        Admin
                      </span>
                    } @else {
                      <span class="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-gray-100 text-gray-700 border border-gray-200">
                        User
                      </span>
                    }
                  </td>
                  <td class="p-4 text-sm text-muted-foreground">
                    {{ formatDate(user.created_at) }}
                  </td>
                  <td class="p-4 text-right">
                    <div class="flex items-center justify-end gap-2">
                      @if (user.active) {
                        <button
                          (click)="confirmDisable(user)"
                          [disabled]="isSelf(user)"
                          class="btn-secondary text-xs px-2 py-1"
                          [class.opacity-50]="isSelf(user)"
                          [class.cursor-not-allowed]="isSelf(user)"
                        >
                          Disable
                        </button>
                      } @else {
                        <button (click)="enableUser(user)" class="btn-secondary text-xs px-2 py-1">
                          Enable
                        </button>
                      }
                      <button (click)="openResetPasswordDialog(user)" class="btn-secondary text-xs px-2 py-1">
                        Reset Password
                      </button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Create User Dialog -->
      @if (showCreateDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-slate-900/50" (click)="closeCreateDialog()"></div>
          <div class="relative bg-white border border-border rounded-lg shadow-lg p-6 w-full max-w-md mx-4">
            <h2 class="text-lg font-semibold mb-4">Create User</h2>
            <form [formGroup]="createForm" (ngSubmit)="createUser()" class="space-y-4">
              <div>
                <label class="label">Email</label>
                <input type="email" formControlName="email" class="input" placeholder="user&#64;example.com" />
                @if (createForm.get('email')?.invalid && createForm.get('email')?.touched) {
                  <p class="text-xs text-red-500 mt-1">Valid email is required</p>
                }
              </div>
              <div>
                <label class="label">Password</label>
                <input type="password" formControlName="password" class="input" placeholder="Min 8 characters" />
                @if (createForm.get('password')?.invalid && createForm.get('password')?.touched) {
                  <p class="text-xs text-red-500 mt-1">Password must be at least 8 characters</p>
                }
              </div>
              <div class="flex gap-3 justify-end mt-6">
                <button type="button" (click)="closeCreateDialog()" class="btn-secondary">Cancel</button>
                <button type="submit" [disabled]="createForm.invalid || creating()" class="btn-primary">
                  @if (creating()) {
                    <svg class="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                  }
                  Create
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Reset Password Dialog -->
      @if (showResetPasswordDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-slate-900/50" (click)="closeResetPasswordDialog()"></div>
          <div class="relative bg-white border border-border rounded-lg shadow-lg p-6 w-full max-w-md mx-4">
            <h2 class="text-lg font-semibold mb-2">Reset Password</h2>
            <p class="text-sm text-muted-foreground mb-4">Set new password for {{ selectedUser()?.email }}</p>
            <form [formGroup]="resetPasswordForm" (ngSubmit)="resetPassword()" class="space-y-4">
              <div>
                <label class="label">New Password</label>
                <input type="password" formControlName="newPassword" class="input" placeholder="Min 8 characters" />
                @if (resetPasswordForm.get('newPassword')?.invalid && resetPasswordForm.get('newPassword')?.touched) {
                  <p class="text-xs text-red-500 mt-1">Password must be at least 8 characters</p>
                }
              </div>
              <div class="flex gap-3 justify-end mt-6">
                <button type="button" (click)="closeResetPasswordDialog()" class="btn-secondary">Cancel</button>
                <button type="submit" [disabled]="resetPasswordForm.invalid || resetting()" class="btn-primary">
                  @if (resetting()) {
                    <svg class="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                  }
                  Reset Password
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Confirm Disable Dialog -->
      <app-confirm-dialog
        [open]="showDisableDialog()"
        title="Disable User"
        [message]="'Are you sure you want to disable ' + (selectedUser()?.email || '') + '? They will not be able to log in.'"
        confirmText="Disable"
        variant="destructive"
        (confirm)="disableUser()"
        (cancel)="closeDisableDialog()"
      />
    </div>
  `
})
export class UserListComponent implements OnInit {
  private adminService = inject(AdminService);
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  users = this.adminService.users;

  showCreateDialog = signal(false);
  showResetPasswordDialog = signal(false);
  showDisableDialog = signal(false);
  selectedUser = signal<User | null>(null);

  creating = signal(false);
  resetting = signal(false);

  createForm: FormGroup;
  resetPasswordForm: FormGroup;

  constructor() {
    this.createForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });

    this.resetPasswordForm = this.fb.group({
      newPassword: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  private loadUsers(): void {
    this.adminService.getUsers().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }

  openCreateDialog(): void {
    this.createForm.reset();
    this.showCreateDialog.set(true);
  }

  closeCreateDialog(): void {
    this.showCreateDialog.set(false);
  }

  createUser(): void {
    if (this.createForm.invalid) return;

    this.creating.set(true);
    this.adminService.createUser(this.createForm.value).subscribe({
      next: () => {
        this.notificationService.success('User created successfully');
        this.closeCreateDialog();
        this.creating.set(false);
      },
      error: (err) => {
        this.notificationService.error(err.error?.message || err.error?.error || 'Failed to create user');
        this.creating.set(false);
      }
    });
  }

  confirmDisable(user: User): void {
    this.selectedUser.set(user);
    this.showDisableDialog.set(true);
  }

  closeDisableDialog(): void {
    this.showDisableDialog.set(false);
  }

  disableUser(): void {
    const user = this.selectedUser();
    if (!user) return;

    this.adminService.disableUser(user.id).subscribe({
      next: () => {
        this.notificationService.success('User disabled');
        this.closeDisableDialog();
      },
      error: (err) => {
        this.notificationService.error(err.error?.message || err.error?.error || 'Failed to disable user');
      }
    });
  }

  enableUser(user: User): void {
    this.adminService.enableUser(user.id).subscribe({
      next: () => this.notificationService.success('User enabled'),
      error: (err) => this.notificationService.error(err.error?.message || err.error?.error || 'Failed to enable user')
    });
  }

  openResetPasswordDialog(user: User): void {
    this.selectedUser.set(user);
    this.resetPasswordForm.reset();
    this.showResetPasswordDialog.set(true);
  }

  closeResetPasswordDialog(): void {
    this.showResetPasswordDialog.set(false);
  }

  resetPassword(): void {
    const user = this.selectedUser();
    if (!user || this.resetPasswordForm.invalid) return;

    this.resetting.set(true);
    this.adminService.resetUserPassword(user.id, { new_password: this.resetPasswordForm.value.newPassword }).subscribe({
      next: () => {
        this.notificationService.success('Password reset successfully');
        this.closeResetPasswordDialog();
        this.resetting.set(false);
      },
      error: (err) => {
        this.notificationService.error(err.error?.message || err.error?.error || 'Failed to reset password');
        this.resetting.set(false);
      }
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  isSelf(user: User): boolean {
    return this.authService.user()?.id === user.id;
  }
}
