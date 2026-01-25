import { Component, OnInit, signal, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClusterService } from '../../../core/services/cluster.service';
import { Location } from '../../../core/models';

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
          <!-- Cluster Name -->
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

          <!-- Node Locations -->
          <div class="space-y-4">
            <label class="label">Node Locations</label>
            <p class="text-xs text-muted-foreground -mt-2">
              Select a region for each of the 3 nodes. You can distribute nodes across different regions for better availability.
            </p>

            @if (locationsLoading()) {
              <div class="flex items-center gap-2 text-muted-foreground py-4">
                <span class="spinner w-4 h-4"></span>
                <span>Loading locations...</span>
              </div>
            } @else if (locationsError()) {
              <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 text-sm">
                {{ locationsError() }}
              </div>
            } @else {
              <div class="space-y-3">
                @for (nodeIndex of [0, 1, 2]; track nodeIndex) {
                  <div class="flex items-center gap-4">
                    <!-- Node label -->
                    <div class="w-20 flex-shrink-0">
                      <span class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Node {{ nodeIndex + 1 }}</span>
                    </div>

                    <!-- Custom dropdown -->
                    <div class="relative flex-1">
                      <!-- Trigger button -->
                      <button
                        type="button"
                        (click)="toggleDropdown(nodeIndex, $event)"
                        class="w-full px-4 py-3 bg-bg-tertiary border text-left flex items-center justify-between transition-colors"
                        [class.border-neon-green]="openDropdown() === nodeIndex"
                        [class.border-status-error]="getSelectedLocation(nodeIndex) && !getSelectedLocation(nodeIndex)?.available"
                        [class.border-border]="openDropdown() !== nodeIndex && (!getSelectedLocation(nodeIndex) || getSelectedLocation(nodeIndex)?.available)"
                        [class.hover:border-muted-foreground]="openDropdown() !== nodeIndex"
                      >
                        @if (getSelectedLocation(nodeIndex); as loc) {
                          <span class="flex items-center gap-3">
                            <span class="text-xl" [class.grayscale]="!loc.available">{{ loc.flag }}</span>
                            <span [class.text-foreground]="loc.available" [class.text-status-error]="!loc.available">{{ loc.countryName }}</span>
                            <span class="text-muted-foreground text-sm">({{ loc.city }})</span>
                            @if (!loc.available) {
                              <span class="text-xs uppercase tracking-wider text-status-error border border-status-error px-2 py-0.5 ml-2">Unavailable</span>
                            }
                          </span>
                        } @else {
                          <span class="text-muted-foreground">Select location...</span>
                        }
                        <!-- Chevron -->
                        <svg
                          class="w-4 h-4 text-muted-foreground transition-transform"
                          [class.rotate-180]="openDropdown() === nodeIndex"
                          fill="none" stroke="currentColor" viewBox="0 0 24 24"
                        >
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                        </svg>
                      </button>

                      <!-- Dropdown panel -->
                      @if (openDropdown() === nodeIndex) {
                        <div class="absolute z-50 w-full mt-1 bg-bg-secondary border border-border shadow-lg shadow-black/50 max-h-64 overflow-y-auto">
                          @for (loc of locations(); track loc.id) {
                            <button
                              type="button"
                              (click)="loc.available && selectLocation(nodeIndex, loc.id)"
                              class="w-full px-4 py-3 flex items-center gap-3 text-left transition-colors border-b border-border last:border-0"
                              [class.bg-neon-green]="nodeRegions()[nodeIndex] === loc.id && loc.available"
                              [class.text-bg-primary]="nodeRegions()[nodeIndex] === loc.id && loc.available"
                              [class.hover:bg-bg-tertiary]="nodeRegions()[nodeIndex] !== loc.id && loc.available"
                              [class.opacity-40]="!loc.available"
                              [class.cursor-not-allowed]="!loc.available"
                              [disabled]="!loc.available"
                            >
                              <span class="text-xl" [class.grayscale]="!loc.available">{{ loc.flag }}</span>
                              <div class="flex-1">
                                <span
                                  [class.text-bg-primary]="nodeRegions()[nodeIndex] === loc.id && loc.available"
                                  [class.text-foreground]="nodeRegions()[nodeIndex] !== loc.id && loc.available"
                                  [class.text-muted-foreground]="!loc.available"
                                >
                                  {{ loc.countryName }}
                                </span>
                                <span
                                  class="text-sm ml-2"
                                  [class.text-bg-primary]="nodeRegions()[nodeIndex] === loc.id && loc.available"
                                  [class.text-muted-foreground]="nodeRegions()[nodeIndex] !== loc.id || !loc.available"
                                >
                                  {{ loc.city }}
                                </span>
                              </div>
                              @if (!loc.available) {
                                <span class="text-xs uppercase tracking-wider text-status-error border border-status-error px-2 py-0.5">Unavailable</span>
                              } @else if (nodeRegions()[nodeIndex] === loc.id) {
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                                </svg>
                              }
                            </button>
                          }
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </div>

          <!-- Features -->
          <div class="bg-bg-tertiary border border-border p-4 space-y-3">
            <h3 class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">All clusters include:</h3>
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
                PostgreSQL 16
              </li>
              <li class="flex items-center gap-2">
                <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                SSL/TLS encryption
              </li>
            </ul>
          </div>

          <!-- Unavailable selection warning -->
          @if (hasUnavailableSelections()) {
            <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 text-sm">
              One or more selected regions are no longer available. Please select different regions.
            </div>
          }

          <div class="flex gap-3 pt-2">
            <button
              type="submit"
              [disabled]="loading() || !canSubmit()"
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
export class ClusterCreateComponent implements OnInit {
  form: FormGroup;
  loading = signal(false);
  error = signal<string | null>(null);

  // Location state
  locations = signal<Location[]>([]);
  locationsLoading = signal(true);
  locationsError = signal<string | null>(null);
  nodeRegions = signal<string[]>(['', '', '']);

  // Dropdown state
  openDropdown = signal<number | null>(null);

  constructor(
    private fb: FormBuilder,
    private clusterService: ClusterService,
    private router: Router,
    private elementRef: ElementRef
  ) {
    this.form = this.fb.group({
      name: ['', [
        Validators.required,
        Validators.minLength(3),
        Validators.pattern(/^[a-z0-9-]+$/)
      ]]
    });
  }

  // Close dropdown when clicking outside
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.openDropdown.set(null);
    }
  }

  ngOnInit(): void {
    this.loadLocations();
  }

  loadLocations(): void {
    this.locationsLoading.set(true);
    this.locationsError.set(null);

    this.clusterService.getLocations().subscribe({
      next: (locs) => {
        this.locations.set(locs);
        this.locationsLoading.set(false);

        // Clear selections that are no longer available
        const regions = [...this.nodeRegions()];
        let changed = false;
        regions.forEach((regionId, index) => {
          if (regionId) {
            const loc = locs.find(l => l.id === regionId);
            if (!loc || !loc.available) {
              regions[index] = '';
              changed = true;
            }
          }
        });
        if (changed) {
          this.nodeRegions.set(regions);
        }
      },
      error: (err) => {
        this.locationsLoading.set(false);
        this.locationsError.set(err.error?.message || 'Failed to load locations');
      }
    });
  }

  toggleDropdown(nodeIndex: number, event: Event): void {
    event.stopPropagation();
    if (this.openDropdown() === nodeIndex) {
      this.openDropdown.set(null);
    } else {
      this.openDropdown.set(nodeIndex);
    }
  }

  selectLocation(nodeIndex: number, locationId: string): void {
    // Only allow selecting available locations
    const loc = this.locations().find(l => l.id === locationId);
    if (!loc?.available) return;

    const regions = [...this.nodeRegions()];
    regions[nodeIndex] = locationId;
    this.nodeRegions.set(regions);
    this.openDropdown.set(null);
  }

  getSelectedLocation(nodeIndex: number): Location | null {
    const regionId = this.nodeRegions()[nodeIndex];
    if (!regionId) return null;
    return this.locations().find(loc => loc.id === regionId) || null;
  }

  canSubmit(): boolean {
    // Form must be valid and all 3 regions must be selected and available
    const regions = this.nodeRegions();
    const allRegionsSelected = regions.every(r => r !== '');
    const allRegionsAvailable = regions.every(r => {
      if (!r) return false;
      const loc = this.locations().find(l => l.id === r);
      return loc?.available === true;
    });
    return this.form.valid && allRegionsSelected && allRegionsAvailable && !this.locationsLoading();
  }

  hasUnavailableSelections(): boolean {
    return this.nodeRegions().some(r => {
      if (!r) return false;
      const loc = this.locations().find(l => l.id === r);
      return loc && !loc.available;
    });
  }

  onSubmit(): void {
    if (!this.canSubmit()) return;

    this.loading.set(true);
    this.error.set(null);

    const request = {
      name: this.form.get('name')?.value,
      nodeRegions: this.nodeRegions()
    };

    this.clusterService.createCluster(request).subscribe({
      next: (cluster) => {
        this.router.navigate(['/clusters', cluster.id]);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || err.error?.error || 'Failed to create cluster. Please try again.');
      }
    });
  }
}
