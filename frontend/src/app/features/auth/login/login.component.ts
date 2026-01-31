import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AuthLayoutComponent } from '../../../shared/layouts/auth-layout/auth-layout.component';
import { SpinnerComponent } from '../../../shared/components';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AuthLayoutComponent, SpinnerComponent],
  template: `
    <app-auth-layout>
      <div class="card">
        <div class="card-header">Sign In</div>

        @if (error()) {
          <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 mb-6 text-sm">
            {{ error() }}
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-5">
          <div>
            <label for="email" class="label">Email</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              class="input"
              [class.input-error]="form.get('email')?.touched && form.get('email')?.invalid"
              placeholder="email@example.com"
            />
            @if (form.get('email')?.touched && form.get('email')?.errors?.['required']) {
              <p class="text-xs text-status-error mt-1">Email is required</p>
            }
            @if (form.get('email')?.touched && form.get('email')?.errors?.['email']) {
              <p class="text-xs text-status-error mt-1">Please enter a valid email</p>
            }
          </div>

          <div>
            <label for="password" class="label">Password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              class="input"
              [class.input-error]="form.get('password')?.touched && form.get('password')?.invalid"
              placeholder="Enter your password"
            />
            @if (form.get('password')?.touched && form.get('password')?.errors?.['required']) {
              <p class="text-xs text-status-error mt-1">Password is required</p>
            }
          </div>

          <button
            type="submit"
            [disabled]="loading() || form.invalid"
            class="btn-primary w-full"
          >
            @if (loading()) {
              <span class="spinner w-4 h-4 mr-2"></span>
            }
            Sign In
          </button>
        </form>

        <div class="mt-6 pt-4 border-t border-border">
          <p class="text-sm text-center text-muted-foreground">
            Contact your administrator to get an account
          </p>
        </div>
      </div>
    </app-auth-layout>
  `
})
export class LoginComponent {
  form: FormGroup;
  loading = signal(false);
  error = signal<string | null>(null);

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    this.authService.login(this.form.value).subscribe({
      next: () => {
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'Invalid email or password');
      }
    });
  }
}
