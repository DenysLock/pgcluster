import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClusterService } from '../../../core/services/cluster.service';
import { SpinnerComponent } from '../../../shared/components';

@Component({
  selector: 'app-cluster-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, SpinnerComponent],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <!-- Header -->
      <div>
        <a routerLink="/clusters" class="text-sm text-muted-foreground hover:text-foreground inline-flex items-center mb-4">
          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Back to Clusters
        </a>
        <h1 class="text-2xl font-bold tracking-tight">Create Cluster</h1>
        <p class="text-muted-foreground">Create a new PostgreSQL cluster with high availability</p>
      </div>

      <!-- Form -->
      <div class="rounded-lg border bg-card p-6">
        @if (error()) {
          <div class="bg-destructive/10 border border-destructive/20 text-destructive px-4 py-3 rounded-md mb-6 text-sm">
            {{ error() }}
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-6">
          <div class="space-y-2">
            <label for="name" class="text-sm font-medium leading-none">Cluster Name</label>
            <input
              id="name"
              type="text"
              formControlName="name"
              class="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              placeholder="my-database"
            />
            <p class="text-xs text-muted-foreground">
              Use lowercase letters, numbers, and hyphens only. This will be used in your connection URL.
            </p>
            @if (form.get('name')?.touched && form.get('name')?.errors?.['required']) {
              <p class="text-xs text-destructive">Cluster name is required</p>
            }
            @if (form.get('name')?.touched && form.get('name')?.errors?.['pattern']) {
              <p class="text-xs text-destructive">Only lowercase letters, numbers, and hyphens allowed</p>
            }
            @if (form.get('name')?.touched && form.get('name')?.errors?.['minlength']) {
              <p class="text-xs text-destructive">Name must be at least 3 characters</p>
            }
          </div>

          <div class="space-y-2">
            <label class="text-sm font-medium leading-none">Plan</label>
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
                    <div class="w-10 h-10 rounded-lg bg-muted flex items-center justify-center shrink-0">
                      <svg class="w-5 h-5 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
                      </svg>
                    </div>
                    <div class="flex-1">
                      <p class="font-medium">{{ plan.name }}</p>
                      <p class="text-sm text-muted-foreground">{{ plan.description }}</p>
                      <p class="text-sm font-medium mt-2">{{ plan.price }}</p>
                    </div>
                  </div>
                  @if (form.get('plan')?.value === plan.id) {
                    <div class="absolute top-3 right-3">
                      <svg class="w-5 h-5 text-primary" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                    </div>
                  }
                </label>
              }
            </div>
          </div>

          <!-- Features -->
          <div class="rounded-lg bg-muted/50 p-4 space-y-3">
            <h3 class="text-sm font-medium">All plans include:</h3>
            <ul class="grid gap-2 text-sm text-muted-foreground">
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                3-node high availability cluster
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                Automatic failover with Patroni
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                PgBouncer connection pooling
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                Floating IP for zero-downtime failover
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                SSL/TLS encryption
              </li>
            </ul>
          </div>

          <div class="flex gap-3">
            <button
              type="button"
              routerLink="/clusters"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-10 px-4 py-2"
            >
              Cancel
            </button>
            <button
              type="submit"
              [disabled]="loading() || form.invalid"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2 flex-1"
            >
              @if (loading()) {
                <app-spinner size="sm" class="mr-2" />
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
      id: 'dedicated-starter',
      name: 'Starter',
      description: 'Perfect for development and small projects',
      price: 'Free during MVP'
    },
    {
      id: 'dedicated-professional',
      name: 'Professional',
      description: 'For production workloads with more resources',
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
      plan: ['dedicated-starter', Validators.required]
    });
  }

  getPlanCardClass(planId: string): string {
    const base = 'relative block cursor-pointer rounded-lg border p-4 transition-colors';
    const isSelected = this.form.get('plan')?.value === planId;
    return isSelected
      ? `${base} border-primary bg-primary/5`
      : `${base} border-border hover:border-primary/50`;
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
