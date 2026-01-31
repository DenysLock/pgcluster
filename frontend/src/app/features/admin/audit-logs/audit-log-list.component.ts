import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { AuditLog, AuditLogListResponse, User, AdminCluster } from '../../../core/models';
import { SpinnerComponent } from '../../../shared/components';

@Component({
  selector: 'app-audit-log-list',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, SpinnerComponent],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-lg font-bold">Audit Logs</h1>
        <p class="text-muted-foreground">System activity and security events</p>
      </div>

      <!-- Filters -->
      <div class="rounded-lg border bg-card p-4">
        <form [formGroup]="filterForm" (ngSubmit)="applyFilters()" class="flex flex-wrap gap-4 items-end">
          <!-- User Filter (Typeahead) -->
          <div class="flex-1 min-w-[200px] relative">
            <label class="label">User</label>
            <input
              type="text"
              formControlName="userSearch"
              placeholder="Search by email..."
              class="input"
              (focus)="showUserDropdown = true"
              (blur)="hideUserDropdown()"
              (input)="onUserSearchChange()"
            />
            @if (showUserDropdown && filteredUsers().length > 0) {
              <div class="absolute z-10 w-full mt-1 bg-card border border-border rounded-md shadow-lg max-h-48 overflow-auto">
                @for (user of filteredUsers(); track user.id) {
                  <button
                    type="button"
                    (mousedown)="selectUser(user)"
                    class="w-full px-3 py-2 text-left text-sm hover:bg-muted"
                  >
                    {{ user.email }}
                  </button>
                }
              </div>
            }
            @if (selectedUser()) {
              <div class="text-xs text-primary mt-1 flex items-center gap-1">
                Selected: {{ selectedUser()!.email }}
                <button type="button" (click)="clearUser()" class="text-muted-foreground hover:text-foreground">✕</button>
              </div>
            }
          </div>

          <!-- Cluster Filter (Typeahead) -->
          <div class="flex-1 min-w-[200px] relative">
            <label class="label">Cluster</label>
            <input
              type="text"
              formControlName="clusterSearch"
              placeholder="Search by slug..."
              class="input"
              (focus)="showClusterDropdown = true"
              (blur)="hideClusterDropdown()"
              (input)="onClusterSearchChange()"
            />
            @if (showClusterDropdown && filteredClusters().length > 0) {
              <div class="absolute z-10 w-full mt-1 bg-card border border-border rounded-md shadow-lg max-h-48 overflow-auto">
                @for (cluster of filteredClusters(); track cluster.id) {
                  <button
                    type="button"
                    (mousedown)="selectCluster(cluster)"
                    class="w-full px-3 py-2 text-left text-sm hover:bg-muted"
                  >
                    {{ cluster.slug }}.db.pgcluster.com
                  </button>
                }
              </div>
            }
            @if (selectedCluster()) {
              <div class="text-xs text-primary mt-1 flex items-center gap-1">
                Selected: {{ selectedCluster()!.slug }}
                <button type="button" (click)="clearCluster()" class="text-muted-foreground hover:text-foreground">✕</button>
              </div>
            }
          </div>

          <div class="flex-1 min-w-[180px]">
            <label class="label">Action Type</label>
            <select formControlName="action" class="input">
              <option value="">All Actions</option>
              <option value="AUTH_LOGIN_SUCCESS">Login Success</option>
              <option value="AUTH_LOGIN_FAILURE">Login Failure</option>
              <option value="USER_CREATED">User Created</option>
              <option value="USER_DISABLED">User Disabled</option>
              <option value="USER_ENABLED">User Enabled</option>
              <option value="USER_PASSWORD_RESET">Password Reset</option>
              <option value="CLUSTER_CREATED">Cluster Created</option>
              <option value="CLUSTER_DELETED">Cluster Deleted</option>
              <option value="BACKUP_INITIATED">Backup Initiated</option>
              <option value="BACKUP_DELETED">Backup Deleted</option>
              <option value="BACKUP_RESTORE_INITIATED">Restore Initiated</option>
              <option value="CREDENTIALS_ACCESSED">Credentials Accessed</option>
              <option value="EXPORT_INITIATED">Export Initiated</option>
              <option value="EXPORT_DELETED">Export Deleted</option>
            </select>
          </div>
          <div class="flex-1 min-w-[180px]">
            <label class="label">Resource Type</label>
            <select formControlName="resourceType" class="input">
              <option value="">All Resources</option>
              <option value="auth">Authentication</option>
              <option value="user">Users</option>
              <option value="cluster">Clusters</option>
              <option value="backup">Backups</option>
              <option value="export">Exports</option>
            </select>
          </div>
          <div class="flex-1 min-w-[140px]">
            <label class="label">Start Date</label>
            <input type="date" formControlName="startDate" class="input" />
          </div>
          <div class="flex-1 min-w-[140px]">
            <label class="label">End Date</label>
            <input type="date" formControlName="endDate" class="input" />
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn-primary">Apply</button>
            <button type="button" (click)="clearFilters()" class="btn-secondary">Clear</button>
          </div>
        </form>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-12">
          <app-spinner size="lg" />
        </div>
      } @else {
        <!-- Logs Table -->
        <div class="rounded-lg border bg-card overflow-hidden">
          <table class="w-full">
            <thead>
              <tr class="border-b bg-muted/50">
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Timestamp</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">User</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Action</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">Resource</th>
                <th class="h-12 px-4 text-left align-middle font-medium text-muted-foreground">IP Address</th>
              </tr>
            </thead>
            <tbody>
              @for (log of logs(); track log.id) {
                <tr class="border-b last:border-0 hover:bg-muted/25">
                  <td class="p-4 text-sm">{{ formatTimestamp(log.timestamp) }}</td>
                  <td class="p-4 text-sm">
                    @if (log.user_id && log.user_email) {
                      <a [routerLink]="['/admin/users', log.user_id]" class="text-primary hover:underline">
                        {{ log.user_email }}
                      </a>
                    } @else {
                      {{ log.user_email || '-' }}
                    }
                  </td>
                  <td class="p-4">
                    <span [class]="getActionBadgeClass(log.action)">
                      {{ formatAction(log.action) }}
                    </span>
                  </td>
                  <td class="p-4 text-sm text-muted-foreground">
                    @if (log.resource_type) {
                      {{ log.resource_type }}
                      @if (getAffectedEmail(log)) {
                        <a [routerLink]="['/admin/users', log.resource_id]" class="text-primary hover:underline ml-1">
                          {{ getAffectedEmail(log) }}
                        </a>
                      } @else if (getClusterInfo(log)) {
                        <a [routerLink]="['/admin/clusters', getClusterInfo(log)!.id]" class="text-primary hover:underline ml-1">
                          {{ getClusterInfo(log)!.name }}
                        </a>
                        @if (getRestoreTarget(log)) {
                          <span class="mx-1">→</span>
                          <a [routerLink]="['/admin/clusters', getRestoreTarget(log)!.id]" class="text-primary hover:underline">
                            {{ getRestoreTarget(log)!.name }}
                          </a>
                        }
                        @if (getRestoredFrom(log)) {
                          <span class="text-xs ml-1">(from
                            <a [routerLink]="['/admin/clusters', getRestoredFrom(log)!.id]" class="text-primary hover:underline">
                              {{ getRestoredFrom(log)!.name }}
                            </a>)
                          </span>
                        }
                      } @else if (log.resource_id) {
                        <span class="text-xs ml-1 font-mono">{{ log.resource_id.substring(0, 8) }}...</span>
                      }
                    } @else {
                      -
                    }
                  </td>
                  <td class="p-4 text-sm text-muted-foreground font-mono">{{ log.ip_address || '-' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="p-8 text-center text-muted-foreground">
                    No audit logs found
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        @if (response()) {
          <div class="flex items-center justify-between">
            <div class="text-sm text-muted-foreground">
              Showing {{ logs().length }} of {{ response()!.total_elements }} entries
            </div>
            <div class="flex gap-2">
              <button
                (click)="previousPage()"
                [disabled]="currentPage() === 0"
                class="btn-secondary text-sm"
                [class.opacity-50]="currentPage() === 0"
              >
                Previous
              </button>
              <span class="flex items-center px-3 text-sm text-muted-foreground">
                Page {{ currentPage() + 1 }} of {{ response()!.total_pages || 1 }}
              </span>
              <button
                (click)="nextPage()"
                [disabled]="currentPage() >= (response()!.total_pages || 1) - 1"
                class="btn-secondary text-sm"
                [class.opacity-50]="currentPage() >= (response()!.total_pages || 1) - 1"
              >
                Next
              </button>
            </div>
          </div>
        }
      }
    </div>
  `
})
export class AuditLogListComponent implements OnInit {
  private adminService = inject(AdminService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  logs = signal<AuditLog[]>([]);
  response = signal<AuditLogListResponse | null>(null);
  currentPage = signal(0);
  users = signal<User[]>([]);
  clusters = signal<AdminCluster[]>([]);

  // Typeahead state
  selectedUser = signal<User | null>(null);
  selectedCluster = signal<AdminCluster | null>(null);
  showUserDropdown = false;
  showClusterDropdown = false;
  private userSearchTerm = signal('');
  private clusterSearchTerm = signal('');

  filterForm: FormGroup;

  // Computed filtered lists
  filteredUsers = computed(() => {
    const search = this.userSearchTerm().toLowerCase();
    if (!search) return this.users().slice(0, 10);
    return this.users().filter(u => u.email.toLowerCase().includes(search)).slice(0, 10);
  });

  filteredClusters = computed(() => {
    const search = this.clusterSearchTerm().toLowerCase();
    if (!search) return this.clusters().slice(0, 10);
    return this.clusters().filter(c => c.slug.toLowerCase().includes(search)).slice(0, 10);
  });

  constructor() {
    this.filterForm = this.fb.group({
      userSearch: [''],
      clusterSearch: [''],
      action: [''],
      resourceType: [''],
      startDate: [''],
      endDate: ['']
    });
  }

  ngOnInit(): void {
    this.loadLogs();
    this.loadFilterOptions();
  }

  private loadFilterOptions(): void {
    this.adminService.getUsers().subscribe({
      next: (res) => this.users.set(res.users || []),
      error: () => {}
    });
    this.adminService.getAdminClusters().subscribe({
      next: (res) => this.clusters.set(res.clusters || []),
      error: () => {}
    });
  }

  // Typeahead handlers
  onUserSearchChange(): void {
    this.userSearchTerm.set(this.filterForm.get('userSearch')?.value || '');
    // Clear selection if user types something different
    if (this.selectedUser() && this.filterForm.get('userSearch')?.value !== this.selectedUser()!.email) {
      this.selectedUser.set(null);
    }
  }

  onClusterSearchChange(): void {
    this.clusterSearchTerm.set(this.filterForm.get('clusterSearch')?.value || '');
    // Clear selection if user types something different
    if (this.selectedCluster() && this.filterForm.get('clusterSearch')?.value !== this.selectedCluster()!.slug) {
      this.selectedCluster.set(null);
    }
  }

  selectUser(user: User): void {
    this.selectedUser.set(user);
    this.filterForm.patchValue({ userSearch: user.email });
    this.userSearchTerm.set(user.email);
    this.showUserDropdown = false;
  }

  selectCluster(cluster: AdminCluster): void {
    this.selectedCluster.set(cluster);
    this.filterForm.patchValue({ clusterSearch: cluster.slug });
    this.clusterSearchTerm.set(cluster.slug);
    this.showClusterDropdown = false;
  }

  clearUser(): void {
    this.selectedUser.set(null);
    this.filterForm.patchValue({ userSearch: '' });
    this.userSearchTerm.set('');
  }

  clearCluster(): void {
    this.selectedCluster.set(null);
    this.filterForm.patchValue({ clusterSearch: '' });
    this.clusterSearchTerm.set('');
  }

  hideUserDropdown(): void {
    setTimeout(() => this.showUserDropdown = false, 200);
  }

  hideClusterDropdown(): void {
    setTimeout(() => this.showClusterDropdown = false, 200);
  }

  private loadLogs(): void {
    this.loading.set(true);
    const filters = this.filterForm.value;

    this.adminService.getAuditLogs({
      page: this.currentPage(),
      size: 50,
      userId: this.selectedUser()?.id || undefined,
      clusterId: this.selectedCluster()?.id || undefined,
      action: filters.action || undefined,
      resourceType: filters.resourceType || undefined,
      startDate: filters.startDate ? new Date(filters.startDate).toISOString() : undefined,
      endDate: filters.endDate ? new Date(filters.endDate + 'T23:59:59').toISOString() : undefined
    }).subscribe({
      next: (res) => {
        this.logs.set(res.logs);
        this.response.set(res);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadLogs();
  }

  clearFilters(): void {
    this.selectedUser.set(null);
    this.selectedCluster.set(null);
    this.userSearchTerm.set('');
    this.clusterSearchTerm.set('');
    this.filterForm.reset({
      userSearch: '',
      clusterSearch: '',
      action: '',
      resourceType: '',
      startDate: '',
      endDate: ''
    });
    this.currentPage.set(0);
    this.loadLogs();
  }

  previousPage(): void {
    if (this.currentPage() > 0) {
      this.currentPage.update(p => p - 1);
      this.loadLogs();
    }
  }

  nextPage(): void {
    const res = this.response();
    if (res && this.currentPage() < res.total_pages - 1) {
      this.currentPage.update(p => p + 1);
      this.loadLogs();
    }
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString('en-US', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  formatAction(action: string): string {
    return action.replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase());
  }

  getAffectedEmail(log: AuditLog): string | null {
    if (!log.details || log.resource_type !== 'user') return null;
    return log.details['enabled_email']
        || log.details['disabled_email']
        || log.details['reset_for_email']
        || log.details['created_email']
        || null;
  }

  getClusterInfo(log: AuditLog): { id: string; name: string } | null {
    if (!log.details) return null;
    if (log.resource_type === 'cluster' && log.resource_id) {
      const name = log.details['cluster_slug'] || log.details['cluster_name'] || null;
      return name ? { id: log.resource_id, name } : null;
    }
    if ((log.resource_type === 'backup' || log.resource_type === 'export') && log.details['cluster_id']) {
      // Prefer cluster_slug over cluster_name for full slug display (e.g., s17-wzjp28 instead of s17)
      const name = log.details['cluster_slug'] || log.details['cluster_name'] || null;
      return name ? { id: log.details['cluster_id'], name } : null;
    }
    return null;
  }

  // For BACKUP_RESTORE_INITIATED: get target cluster info
  getRestoreTarget(log: AuditLog): { id: string; name: string } | null {
    if (!log.details || log.action !== 'BACKUP_RESTORE_INITIATED') return null;
    const targetId = log.details['target_cluster_id'];
    const targetName = log.details['target_cluster_slug'] || log.details['target_cluster_name'];
    if (targetId && targetName && targetName !== 'in-place') {
      return { id: targetId, name: targetName };
    }
    return null;
  }

  // For CLUSTER_CREATED: get "restored from" info if cluster was created from restore
  getRestoredFrom(log: AuditLog): { id: string; name: string } | null {
    if (!log.details || log.action !== 'CLUSTER_CREATED') return null;
    const id = log.details['restored_from_cluster_id'];
    const name = log.details['restored_from_cluster_slug'];
    if (id && name) {
      return { id, name };
    }
    return null;
  }

  getActionBadgeClass(action: string): string {
    const base = 'inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full';
    if (action.includes('FAILURE') || action.includes('DELETED') || action.includes('DISABLED')) {
      return `${base} bg-red-100 text-red-700 border border-red-200`;
    }
    if (action.includes('CREATED') || action.includes('SUCCESS') || action.includes('ENABLED')) {
      return `${base} bg-green-100 text-green-700 border border-green-200`;
    }
    return `${base} bg-gray-100 text-gray-700 border border-gray-200`;
  }
}
