import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AuthLayoutComponent } from '../../../shared/layouts/auth-layout/auth-layout.component';
import { SpinnerComponent } from '../../../shared/components';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AuthLayoutComponent, SpinnerComponent],
  template: `
    <app-auth-layout>
      <div class="card">
        <div class="card-header">Create Account</div>

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
              placeholder="Create a password"
            />
            @if (form.get('password')?.touched && form.get('password')?.errors?.['required']) {
              <p class="text-xs text-status-error mt-1">Password is required</p>
            }
            @if (form.get('password')?.touched && form.get('password')?.errors?.['minlength']) {
              <p class="text-xs text-status-error mt-1">Password must be at least 8 characters</p>
            }
          </div>

          <div>
            <label for="confirmPassword" class="label">Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              formControlName="confirmPassword"
              class="input"
              [class.input-error]="form.get('confirmPassword')?.touched && (form.get('confirmPassword')?.invalid || form.errors?.['passwordMismatch'])"
              placeholder="Confirm your password"
            />
            @if (form.get('confirmPassword')?.touched && form.get('confirmPassword')?.errors?.['required']) {
              <p class="text-xs text-status-error mt-1">Please confirm your password</p>
            }
            @if (form.errors?.['passwordMismatch'] && form.get('confirmPassword')?.touched) {
              <p class="text-xs text-status-error mt-1">Passwords do not match</p>
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
            Create Account
          </button>
        </form>

        <div class="mt-6 pt-4 border-t border-border">
          <p class="text-sm text-center text-muted-foreground">
            Already have an account?
            <a routerLink="/login" class="text-neon-green hover:underline font-medium ml-1">Sign in</a>
          </p>
        </div>
      </div>
    </app-auth-layout>
  `
})
export class RegisterComponent {
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
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator(form: FormGroup) {
    const password = form.get('password');
    const confirmPassword = form.get('confirmPassword');
    if (password && confirmPassword && password.value !== confirmPassword.value) {
      return { passwordMismatch: true };
    }
    return null;
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    const { email, password } = this.form.value;

    this.authService.register({ email, password }).subscribe({
      next: () => {
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'Registration failed. Please try again.');
      }
    });
  }
}
