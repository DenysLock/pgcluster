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
      <div class="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div class="p-6">
          <h2 class="text-xl font-semibold mb-1">Create an account</h2>
          <p class="text-sm text-muted-foreground mb-6">Enter your details to get started</p>

          @if (error()) {
            <div class="bg-destructive/10 border border-destructive/20 text-destructive px-4 py-3 rounded-md mb-4 text-sm">
              {{ error() }}
            </div>
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="space-y-2">
              <label for="email" class="text-sm font-medium leading-none">Email</label>
              <input
                id="email"
                type="email"
                formControlName="email"
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                placeholder="email@example.com"
              />
              @if (form.get('email')?.touched && form.get('email')?.errors?.['required']) {
                <p class="text-xs text-destructive">Email is required</p>
              }
              @if (form.get('email')?.touched && form.get('email')?.errors?.['email']) {
                <p class="text-xs text-destructive">Please enter a valid email</p>
              }
            </div>

            <div class="space-y-2">
              <label for="password" class="text-sm font-medium leading-none">Password</label>
              <input
                id="password"
                type="password"
                formControlName="password"
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                placeholder="Create a password"
              />
              @if (form.get('password')?.touched && form.get('password')?.errors?.['required']) {
                <p class="text-xs text-destructive">Password is required</p>
              }
              @if (form.get('password')?.touched && form.get('password')?.errors?.['minlength']) {
                <p class="text-xs text-destructive">Password must be at least 8 characters</p>
              }
            </div>

            <div class="space-y-2">
              <label for="confirmPassword" class="text-sm font-medium leading-none">Confirm Password</label>
              <input
                id="confirmPassword"
                type="password"
                formControlName="confirmPassword"
                class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                placeholder="Confirm your password"
              />
              @if (form.get('confirmPassword')?.touched && form.get('confirmPassword')?.errors?.['required']) {
                <p class="text-xs text-destructive">Please confirm your password</p>
              }
              @if (form.errors?.['passwordMismatch'] && form.get('confirmPassword')?.touched) {
                <p class="text-xs text-destructive">Passwords do not match</p>
              }
            </div>

            <button
              type="submit"
              [disabled]="loading() || form.invalid"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2 w-full"
            >
              @if (loading()) {
                <app-spinner size="sm" class="mr-2" />
              }
              Create account
            </button>
          </form>
        </div>

        <div class="p-6 pt-0">
          <p class="text-sm text-center text-muted-foreground">
            Already have an account?
            <a routerLink="/login" class="text-primary hover:underline font-medium">Sign in</a>
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
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'Registration failed. Please try again.');
      }
    });
  }
}
