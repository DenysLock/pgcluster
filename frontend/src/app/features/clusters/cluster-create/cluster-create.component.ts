import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClusterService } from '../../../core/services/cluster.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Location, ServerType, ServerTypesResponse } from '../../../core/models';

@Component({
  selector: 'app-cluster-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="max-w-3xl mx-auto space-y-6">
      <!-- Header -->
      <div>
        <h1 class="text-xl font-semibold uppercase tracking-wider text-foreground">Create Cluster</h1>
        <p class="text-muted-foreground text-sm mt-1">Deploy a new PostgreSQL cluster</p>
      </div>

      <!-- Form -->
      <div class="card">
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

          <!-- PostgreSQL Version -->
          <div class="space-y-2">
            <label class="label">PostgreSQL Version</label>
            <div class="flex gap-2">
              @for (version of availableVersions; track version) {
                <button
                  type="button"
                  (click)="postgresVersion.set(version)"
                  class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border transition-colors"
                  [class.bg-neon-green]="postgresVersion() === version"
                  [class.text-bg-primary]="postgresVersion() === version"
                  [class.border-neon-green]="postgresVersion() === version"
                  [class.bg-transparent]="postgresVersion() !== version"
                  [class.text-muted-foreground]="postgresVersion() !== version"
                  [class.border-border]="postgresVersion() !== version"
                  [class.hover:border-muted-foreground]="postgresVersion() !== version"
                >
                  {{ version }}
                </button>
              }
            </div>
            <p class="text-xs text-muted-foreground">
              PostgreSQL 16 is recommended for most workloads.
            </p>
          </div>

          <!-- Server Type Selection -->
          <div class="space-y-4">
            <label class="label">Server Type</label>

            @if (serverTypesLoading()) {
              <div class="flex items-center gap-2 text-muted-foreground py-4">
                <span class="spinner w-4 h-4"></span>
                <span>Loading server types...</span>
              </div>
            } @else if (serverTypesError()) {
              <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 text-sm">
                {{ serverTypesError() }}
              </div>
            } @else {
              <!-- Category Tabs -->
              <div class="flex gap-2 mb-4">
                <button
                  type="button"
                  (click)="selectCategory('shared')"
                  class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border transition-colors"
                  [class.bg-neon-green]="selectedCategory() === 'shared'"
                  [class.text-bg-primary]="selectedCategory() === 'shared'"
                  [class.border-neon-green]="selectedCategory() === 'shared'"
                  [class.bg-transparent]="selectedCategory() !== 'shared'"
                  [class.text-muted-foreground]="selectedCategory() !== 'shared'"
                  [class.border-border]="selectedCategory() !== 'shared'"
                  [class.hover:border-muted-foreground]="selectedCategory() !== 'shared'"
                >
                  Shared vCPU
                </button>
                <button
                  type="button"
                  (click)="selectCategory('dedicated')"
                  class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border transition-colors"
                  [class.bg-neon-green]="selectedCategory() === 'dedicated'"
                  [class.text-bg-primary]="selectedCategory() === 'dedicated'"
                  [class.border-neon-green]="selectedCategory() === 'dedicated'"
                  [class.bg-transparent]="selectedCategory() !== 'dedicated'"
                  [class.text-muted-foreground]="selectedCategory() !== 'dedicated'"
                  [class.border-border]="selectedCategory() !== 'dedicated'"
                  [class.hover:border-muted-foreground]="selectedCategory() !== 'dedicated'"
                >
                  Dedicated vCPU
                </button>
              </div>

              <!-- Server Type Cards -->
              <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
                @for (type of currentServerTypes(); track type.name) {
                  <button
                    type="button"
                    (click)="selectServerType(type)"
                    [ngClass]="getServerTypeClass(type)"
                    [disabled]="!isAvailable(type)"
                  >
                    <div class="font-semibold text-foreground mb-1">{{ type.name }}</div>
                    <div class="text-xs text-muted-foreground space-y-0.5">
                      <div>{{ type.cores }} vCPU</div>
                      <div>{{ type.memory }} GB RAM</div>
                      <div>{{ type.disk }} GB SSD</div>
                    </div>
                    <div class="mt-2" [class.hidden]="isAvailable(type)">
                      <span class="text-xs uppercase tracking-wider text-status-error border border-status-error px-2 py-0.5">Unavailable</span>
                    </div>
                  </button>
                }
              </div>
            }
          </div>

          <!-- Cluster Mode Toggle -->
          <div class="space-y-3">
            <label class="label">Cluster Mode</label>
            <div class="flex gap-3">
              <button
                type="button"
                (click)="toggleHaMode(false)"
                [ngClass]="!haMode() ? 'flex-1 p-4 border text-left transition-all border-neon-green bg-neon-green/10' : 'flex-1 p-4 border text-left transition-all border-border hover:border-muted-foreground'"
              >
                <div class="font-semibold text-foreground">Single Node</div>
                <div class="text-xs text-muted-foreground mt-1">1 node, lower cost, no HA</div>
              </button>
              <button
                type="button"
                (click)="toggleHaMode(true)"
                [ngClass]="haMode() ? 'flex-1 p-4 border text-left transition-all border-neon-green bg-neon-green/10' : 'flex-1 p-4 border text-left transition-all border-border hover:border-muted-foreground'"
              >
                <div class="font-semibold text-foreground">High Availability</div>
                <div class="text-xs text-muted-foreground mt-1">3 nodes, auto-failover</div>
              </button>
            </div>
          </div>

          <!-- Node Locations as Button Grid -->
          <div class="space-y-4">
            <label class="label">Node {{ haMode() ? 'Locations' : 'Location' }}</label>
            <p class="text-xs text-muted-foreground -mt-2">
              Select a region for {{ haMode() ? 'each node' : 'your node' }}. Grayed buttons are unavailable for the selected server type.
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
              <div class="space-y-4">
                @for (nodeIndex of (haMode() ? [0, 1, 2] : [0]); track nodeIndex) {
                  <div class="space-y-2">
                    <span class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      {{ haMode() ? 'Node ' + (nodeIndex + 1) : 'Node' }}
                    </span>
                    <div class="grid grid-cols-3 md:grid-cols-6 gap-1.5">
                      @for (loc of filteredLocations(); track loc.id) {
                        <button
                          type="button"
                          (click)="selectLocation(nodeIndex, loc.id)"
                          [ngClass]="getLocationClass(nodeIndex, loc)"
                          [disabled]="!loc.available"
                        >
                          <span>{{ loc.flag }}</span>
                          <span class="text-[10px] truncate">{{ loc.city }}</span>
                        </button>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </div>

          <!-- Features -->
          <div class="bg-bg-tertiary border border-border p-4 space-y-3">
            <h3 class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {{ haMode() ? 'HA cluster includes:' : 'Single node includes:' }}
            </h3>
            <ul class="grid gap-2 text-sm text-gray-300">
              @if (haMode()) {
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
              } @else {
                <li class="flex items-center gap-2">
                  <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  Single node deployment
                </li>
                <li class="flex items-center gap-2">
                  <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  Auto-restart on crash
                </li>
                <li class="flex items-center gap-2">
                  <svg class="w-4 h-4 text-neon-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  Upgrade path to 3-node HA
                </li>
              }
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
                PostgreSQL {{ postgresVersion() }}
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

          <!-- Submit Button / Loading State -->
          @if (loading()) {
            <div class="bg-bg-tertiary border border-border p-6 text-center space-y-4">
              <div class="flex justify-center">
                <span class="spinner w-8 h-8"></span>
              </div>
              <div class="space-y-2">
                <p class="text-foreground font-semibold">Creating your servers...</p>
                <p class="text-sm text-muted-foreground">
                  This may take up to a minute while we provision your infrastructure.
                </p>
              </div>
            </div>
          } @else {
            <div class="flex gap-3 pt-2">
              <button
                type="button"
                (click)="onCreateClick()"
                class="btn-primary flex-1"
              >
                Create Cluster
              </button>
            </div>
          }
        </form>
      </div>
    </div>
  `
})
export class ClusterCreateComponent implements OnInit {
  form: FormGroup;
  loading = signal(false);

  // Server types state
  serverTypes = signal<ServerTypesResponse | null>(null);
  serverTypesLoading = signal(true);
  serverTypesError = signal<string | null>(null);
  selectedCategory = signal<'shared' | 'dedicated'>('shared');
  selectedServerType = signal<string>('cx23');

  // Location state
  locations = signal<Location[]>([]);
  locationsLoading = signal(true);
  locationsError = signal<string | null>(null);
  nodeRegions = signal<string[]>(['', '', '']);

  // HA mode: true = 3 nodes (HA), false = 1 node (single)
  haMode = signal<boolean>(true);

  // PostgreSQL version
  postgresVersion = signal<string>('16');
  availableVersions = ['14', '15', '16', '17'];

  // Computed: current server types based on selected category
  currentServerTypes = computed(() => {
    const types = this.serverTypes();
    if (!types) return [];
    return this.selectedCategory() === 'shared' ? types.shared : types.dedicated;
  });

  // Computed: filter locations based on selected server type availability
  filteredLocations = computed(() => {
    const locs = this.locations();
    const types = this.serverTypes();
    const selectedType = this.selectedServerType();

    if (!types) return locs;

    // Find the selected server type
    const allTypes = [...types.shared, ...types.dedicated];
    const serverType = allTypes.find(t => t.name === selectedType);

    if (!serverType) return locs;

    // Mark locations as available/unavailable based on server type
    return locs.map(loc => ({
      ...loc,
      available: serverType.availableLocations.includes(loc.id)
    }));
  });

  constructor(
    private fb: FormBuilder,
    private clusterService: ClusterService,
    private notificationService: NotificationService,
    private router: Router
  ) {
    this.form = this.fb.group({
      name: ['', [
        Validators.required,
        Validators.minLength(3),
        Validators.pattern(/^[a-z0-9-]+$/)
      ]]
    });
  }

  ngOnInit(): void {
    this.loadServerTypes();
    this.loadLocations();
  }

  loadServerTypes(): void {
    this.serverTypesLoading.set(true);
    this.serverTypesError.set(null);

    this.clusterService.getServerTypes().subscribe({
      next: (types) => {
        this.serverTypes.set(types);
        this.serverTypesLoading.set(false);

        // Ensure default is available, otherwise pick first available
        if (types.shared.length > 0) {
          const cx23 = types.shared.find(t => t.name === 'cx23');
          if (cx23 && cx23.availableLocations.length > 0) {
            this.selectedServerType.set('cx23');
          } else {
            const firstAvailable = types.shared.find(t => t.availableLocations.length > 0);
            if (firstAvailable) {
              this.selectedServerType.set(firstAvailable.name);
            }
          }
        }
      },
      error: (err) => {
        this.serverTypesLoading.set(false);
        this.serverTypesError.set(err.error?.message || 'Failed to load server types');
      }
    });
  }

  loadLocations(): void {
    this.locationsLoading.set(true);
    this.locationsError.set(null);

    this.clusterService.getLocations().subscribe({
      next: (locs) => {
        this.locations.set(locs);
        this.locationsLoading.set(false);
      },
      error: (err) => {
        this.locationsLoading.set(false);
        this.locationsError.set(err.error?.message || 'Failed to load locations');
      }
    });
  }

  selectCategory(category: 'shared' | 'dedicated'): void {
    if (this.selectedCategory() === category) return;
    this.selectedCategory.set(category);

    // Select first available server type in new category
    const types = this.serverTypes();
    if (types) {
      const categoryTypes = category === 'shared' ? types.shared : types.dedicated;
      const firstAvailable = categoryTypes.find(t => t.availableLocations.length > 0);
      if (firstAvailable) {
        this.selectedServerType.set(firstAvailable.name);
        this.clearInvalidRegions();
      }
    }
  }

  selectServerType(type: ServerType): void {
    if (!this.isAvailable(type)) return;
    this.selectedServerType.set(type.name);
    this.clearInvalidRegions();
  }

  isAvailable(type: ServerType): boolean {
    return type.availableLocations.length > 0;
  }

  getServerTypeClass(type: ServerType): string {
    const base = 'p-4 border text-left transition-all';
    const isSelected = this.selectedServerType() === type.name;
    const available = this.isAvailable(type);

    if (!available) {
      return `${base} opacity-40 cursor-not-allowed border-border`;
    }
    if (isSelected) {
      return `${base} border-neon-green bg-neon-green/10`;
    }
    return `${base} border-border hover:border-muted-foreground`;
  }

  toggleHaMode(enabled: boolean): void {
    if (this.haMode() === enabled) return;
    this.haMode.set(enabled);
    if (enabled) {
      // Switch to HA: 3 nodes
      this.nodeRegions.set(['', '', '']);
    } else {
      // Switch to single node: 1 node
      this.nodeRegions.set(['']);
    }
  }

  clearInvalidRegions(): void {
    // Clear region selections that are no longer valid for the new server type
    const regions = [...this.nodeRegions()];
    const filtered = this.filteredLocations();
    let changed = false;

    regions.forEach((regionId, index) => {
      if (regionId) {
        const loc = filtered.find(l => l.id === regionId);
        if (!loc || !loc.available) {
          regions[index] = '';
          changed = true;
        }
      }
    });

    if (changed) {
      this.nodeRegions.set(regions);
    }
  }

  selectLocation(nodeIndex: number, locationId: string): void {
    const loc = this.filteredLocations().find(l => l.id === locationId);
    if (!loc?.available) return;

    const regions = [...this.nodeRegions()];
    regions[nodeIndex] = locationId;
    this.nodeRegions.set(regions);
  }

  getLocationClass(nodeIndex: number, loc: Location): string {
    const base = 'px-1.5 py-2 border text-center transition-all flex flex-col items-center gap-0.5 rounded';
    const isSelected = this.nodeRegions()[nodeIndex] === loc.id;

    if (!loc.available) {
      return `${base} opacity-30 cursor-not-allowed border-border grayscale`;
    }
    if (isSelected) {
      return `${base} border-neon-green bg-neon-green/10`;
    }
    return `${base} border-border hover:border-muted-foreground`;
  }

  canSubmit(): boolean {
    const regions = this.nodeRegions();
    const allRegionsSelected = regions.every(r => r !== '');
    const allRegionsAvailable = regions.every(r => {
      if (!r) return false;
      const loc = this.filteredLocations().find(l => l.id === r);
      return loc?.available === true;
    });
    return this.form.valid &&
           allRegionsSelected &&
           allRegionsAvailable &&
           !this.locationsLoading() &&
           !this.serverTypesLoading() &&
           !!this.selectedServerType();
  }

  hasUnavailableSelections(): boolean {
    return this.nodeRegions().some(r => {
      if (!r) return false;
      const loc = this.filteredLocations().find(l => l.id === r);
      return loc && !loc.available;
    });
  }

  onCreateClick(): void {
    // Validate and show appropriate error messages
    const nameControl = this.form.get('name');
    const name = nameControl?.value?.trim() || '';

    if (!name) {
      this.notificationService.error('Please enter a cluster name');
      nameControl?.markAsTouched();
      return;
    }

    if (name.length < 3) {
      this.notificationService.error('Cluster name must be at least 3 characters');
      nameControl?.markAsTouched();
      return;
    }

    if (!/^[a-z0-9-]+$/.test(name)) {
      this.notificationService.error('Cluster name can only contain lowercase letters, numbers, and hyphens');
      nameControl?.markAsTouched();
      return;
    }

    const regions = this.nodeRegions();
    const allRegionsSelected = regions.every(r => r !== '');
    if (!allRegionsSelected) {
      const nodeCount = this.haMode() ? 3 : 1;
      this.notificationService.error(`Please select a region for ${nodeCount === 1 ? 'your node' : 'all ' + nodeCount + ' nodes'}`);
      return;
    }

    if (this.hasUnavailableSelections()) {
      this.notificationService.error('One or more selected regions are unavailable. Please select different regions.');
      return;
    }

    // All validations passed, submit
    this.onSubmit();
  }

  onSubmit(): void {
    this.loading.set(true);

    const request = {
      name: this.form.get('name')?.value,
      nodeSize: this.selectedServerType(),
      nodeRegions: this.nodeRegions(),
      postgresVersion: this.postgresVersion()
    };

    this.clusterService.createCluster(request).subscribe({
      next: (cluster) => {
        this.router.navigate(['/clusters', cluster.id]);
      },
      error: (err) => {
        this.loading.set(false);
        const message = err.error?.message || err.error?.error || 'Failed to create cluster. Please try again.';
        this.notificationService.error(message);
      }
    });
  }
}
