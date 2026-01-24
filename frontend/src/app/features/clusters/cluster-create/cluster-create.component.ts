import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClusterService } from '../../../core/services/cluster.service';

@Component({
  selector: 'app-cluster-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <!-- Header -->
      <div>
        <h1 class="text-xl font-semibold uppercase tracking-wider text-foreground">Create Cluster</h1>
        <p class="text-muted-foreground text-sm mt-1">Deploy a new PostgreSQL cluster with high availability</p>
      </div>

      <!-- Form -->
      <div class="card">
        @if (error()) {
          <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 mb-6 text-sm">
            {{ error() }}
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-6">
          <div class="space-y-2">
            <label for="name" class="label">Cluster Name</label>
            <input
              id="name"
              type="text"
              formControlName="name"
              class="input"
              [class.border-status-error]="form.get('name')?.touched && form.get('name')?.invalid"
              placeholder="my-database"
            />
            <p class="text-xs text-muted-foreground">
              Use lowercase letters, numbers, and hyphens only. This will be used in your connection URL.
            </p>
            @if (form.get('name')?.touched && form.get('name')?.errors?.['required']) {
              <p class="text-xs text-status-error">Cluster name is required</p>
            }
            @if (form.get('name')?.touched && form.get('name')?.errors?.['pattern']) {
              <p class="text-xs text-status-error">Only lowercase letters, numbers, and hyphens allowed</p>
            }
            @if (form.get('name')?.touched && form.get('name')?.errors?.['minlength']) {
              <p class="text-xs text-status-error">Name must be at least 3 characters</p>
            }
          </div>

          <div class="space-y-2">
            <label class="label">Plan</label>
            <div class="grid gap-4 md:grid-cols-2">
              @for (plan of plans; track plan.id) {
                <label
                  [class]="getPlanCardClass(plan.id)"
                  [for]="'plan-' + plan.id"
                >
                  <input
                    type="radio"
                    [id]="'plan-' + plan.id"
                    [value]="plan.id"
                    formControlName="plan"
                    class="sr-only"
                  />
                  <div class="flex items-start gap-3">
                    <div class="w-10 h-10 bg-bg-tertiary border border-border flex items-center justify-center shrink-0">
                      <svg class="w-5 h-5 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
                      </svg>
                    </div>
                    <div class="flex-1">
                      <p class="font-semibold text-foreground">{{ plan.name }}</p>
                      <p class="text-sm text-muted-foreground">{{ plan.description }}</p>
                      <p class="text-sm font-semibold text-neon-green mt-2">{{ plan.price }}</p>
                    </div>
                  </div>
                  @if (form.get('plan')?.value === plan.id) {
                    <div class="absolute top-3 right-3">
                      <svg class="w-5 h-5 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                      </svg>
                    </div>
                  }
                </label>
              }
            </div>
          </div>

          <!-- Features -->
          <div class="bg-bg-tertiary border border-border p-4 space-y-3">
            <h3 class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">All plans include:</h3>
            <ul class="grid gap-2 text-sm text-gray-300">
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                3-node high availability cluster
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                Automatic failover with Patroni
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                PgBouncer connection pooling
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                Floating IP for zero-downtime failover
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                SSL/TLS encryption
              </li>
            </ul>
          </div>

          <div class="flex gap-3 pt-2">
            <button
              type="submit"
              [disabled]="loading() || form.invalid"
              class="btn-primary flex-1"
            >
              @if (loading()) {
                <span class="spinner w-4 h-4 mr-2"></span>
              }
              Create Cluster
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class ClusterCreateComponent {
  form: FormGroup;
  loading = signal(false);
  error = signal<string | null>(null);

  plans = [
    {
      id: 'dedicated',
      name: 'Dedicated',
      description: '3-node high availability PostgreSQL cluster',
      price: 'Free during MVP'
    }
  ];

  constructor(
    private fb: FormBuilder,
    private clusterService: ClusterService,
    private router: Router
  ) {
    this.form = this.fb.group({
      name: ['', [
        Validators.required,
        Validators.minLength(3),
        Validators.pattern(/^[a-z0-9-]+$/)
      ]],
      plan: ['dedicated', Validators.required]
    });
  }

  getPlanCardClass(planId: string): string {
    const base = 'relative block cursor-pointer border p-4 transition-colors bg-bg-secondary';
    const isSelected = this.form.get('plan')?.value === planId;
    return isSelected
      ? `${base} border-neon-green`
      : `${base} border-border hover:border-neon-green/50`;
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    this.clusterService.createCluster(this.form.value).subscribe({
      next: (cluster) => {
        this.router.navigate(['/clusters', cluster.id]);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'Failed to create cluster. Please try again.');
      }
    });
  }
}
