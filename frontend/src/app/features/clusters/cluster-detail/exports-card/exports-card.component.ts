import { Component, Input, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';
import { BackupService } from '../../../../core/services/backup.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Export, ExportStatus } from '../../../../core/models';
import { POLLING_INTERVALS } from '../../../../core/constants';
import { ConfirmDialogComponent } from '../../../../shared/components';

@Component({
  selector: 'app-exports-card',
  standalone: true,
  imports: [
    CommonModule,
    ConfirmDialogComponent
  ],
  template: `
    <div class="card">
      <div class="card-header">Exports</div>
      <div class="space-y-6">
        <!-- Action Button -->
        <div class="flex items-center justify-end">
          <button
            (click)="startExport()"
            [disabled]="!isClusterRunning || exporting()"
            class="btn-primary h-9 px-4"
          >
            @if (exporting()) {
              <span class="spinner w-4 h-4 mr-2"></span>
              Creating...
            } @else {
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              Export Database
            }
          </button>
        </div>

        <!-- Loading state -->
        @if (loading()) {
          <div class="flex items-center justify-center py-8">
            <span class="spinner w-6 h-6"></span>
          </div>
        } @else if (exports().length === 0) {
          <!-- Empty state -->
          <div class="text-center py-8 text-muted-foreground">
            <svg class="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            <p class="font-semibold text-foreground">No exports yet</p>
            <p class="text-sm mt-1">Export your database to create a portable SQL dump</p>
          </div>
        } @else {
          <!-- Exports list -->
          <div class="border border-border divide-y divide-border">
            @for (exp of exports(); track exp.id) {
              <div class="p-4 hover:bg-bg-tertiary transition-colors">
                <div class="flex items-start justify-between">
                  <div class="flex items-start gap-3">
                    <!-- Status indicator -->
                    <div [class]="getStatusDotClasses(exp.status)" class="w-2 h-2 mt-2"></div>

                    <div class="flex-1">
                      <!-- Header with badges -->
                      <div class="flex items-center gap-2 flex-wrap">
                        <span class="text-sm font-semibold text-foreground">Database Export</span>
                        <span class="badge badge-info">
                          {{ exp.format || 'pg_dump' }}
                        </span>
                        <span class="badge" [ngClass]="getStatusBadgeClasses(exp.status)">
                          {{ formatStatus(exp.status) }}
                        </span>
                      </div>

                      <!-- Date and size -->
                      <div class="text-sm text-muted-foreground mt-1">
                        {{ formatDate(exp.createdAt) }}
                        @if (exp.formattedSize) {
                          <span class="mx-1">&middot;</span>
                          {{ exp.formattedSize }}
                        }
                      </div>

                      <!-- Expiry info for completed exports -->
                      @if (exp.status === 'completed' && exp.downloadExpiresAt) {
                        @if (isUrlExpired(exp.downloadExpiresAt)) {
                          <div class="text-xs text-status-warning mt-1 flex items-center gap-1">
                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                            Download link expired
                          </div>
                        } @else {
                          <div class="text-xs text-muted-foreground mt-1">
                            Link expires: {{ formatDateTime(exp.downloadExpiresAt) }}
                          </div>
                        }
                      }

                      <!-- Progress for in-progress exports -->
                      @if (exp.status === 'in_progress' || exp.status === 'pending') {
                        <div class="mt-2 flex items-center gap-2">
                          <span class="spinner w-4 h-4"></span>
                          <span class="text-xs text-muted-foreground">
                            {{ exp.status === 'pending' ? 'Waiting to start...' : 'Exporting database...' }}
                          </span>
                        </div>
                      }

                      <!-- Error message for failed exports -->
                      @if (exp.status === 'failed' && exp.errorMessage) {
                        <div class="text-xs text-status-error mt-1">{{ exp.errorMessage }}</div>
                      }
                    </div>
                  </div>

                  <!-- Actions -->
                  <div class="flex items-center gap-2 ml-4">
                    @if (exp.status === 'completed') {
                      @if (isUrlExpired(exp.downloadExpiresAt)) {
                        <button
                          (click)="refreshDownloadUrl(exp)"
                          [disabled]="refreshingUrl() === exp.id"
                          class="btn-secondary h-8 px-3 text-sm"
                        >
                          @if (refreshingUrl() === exp.id) {
                            <span class="spinner w-4 h-4 mr-1"></span>
                          } @else {
                            <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                            </svg>
                          }
                          Refresh Link
                        </button>
                      } @else if (exp.downloadUrl) {
                        <a
                          [href]="exp.downloadUrl"
                          target="_blank"
                          class="btn-secondary inline-flex items-center h-8 px-3 text-sm"
                        >
                          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                          </svg>
                          Download
                        </a>
                      }
                    }
                    @if (exp.status === 'in_progress' || exp.status === 'pending') {
                      <span class="spinner w-4 h-4"></span>
                    } @else {
                      <button
                        (click)="confirmDelete(exp)"
                        [disabled]="deleting() === exp.id"
                        class="text-muted-foreground hover:text-status-error transition-colors h-8 w-8 flex items-center justify-center disabled:opacity-50"
                      >
                        @if (deleting() === exp.id) {
                          <span class="spinner w-4 h-4"></span>
                        } @else {
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        }
                      </button>
                    }
                  </div>
                </div>
              </div>
            }
          </div>
        }
      </div>

      <!-- Delete Confirmation Dialog -->
      <app-confirm-dialog
        [open]="showDeleteDialog()"
        title="Delete Export"
        message="Are you sure you want to delete this export? This action cannot be undone."
        confirmText="Delete"
        variant="destructive"
        (confirm)="deleteExport()"
        (cancel)="closeDeleteDialog()"
      />
    </div>
  `
})
export class ExportsCardComponent implements OnInit, OnDestroy {
  @Input({ required: true }) clusterId!: string;
  @Input() isClusterRunning: boolean = false;

  loading = signal(true);
  exporting = signal(false);
  refreshingUrl = signal<string | null>(null);
  deleting = signal<string | null>(null);
  showDeleteDialog = signal(false);
  exports = signal<Export[]>([]);

  private pollingSubscription?: Subscription;
  private exportToDelete: Export | null = null;

  constructor(
    private backupService: BackupService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.loadExports();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }

  loadExports(): void {
    this.backupService.getExports(this.clusterId).subscribe({
      next: (exports) => {
        this.exports.set(exports);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.notificationService.error(err.error?.message || 'Failed to load exports');
      }
    });
  }

  private startPolling(): void {
    this.pollingSubscription = interval(POLLING_INTERVALS.EXPORT_STATUS).subscribe(() => {
      const hasInProgress = this.exports().some(e => e.status === 'in_progress' || e.status === 'pending');
      if (hasInProgress) {
        this.loadExports();
      }
    });
  }

  startExport(): void {
    this.exporting.set(true);
    this.backupService.createExport(this.clusterId).subscribe({
      next: (exp) => {
        this.exports.update(list => [exp, ...list]);
        this.exporting.set(false);
        this.notificationService.success('Export started');
      },
      error: (err) => {
        this.exporting.set(false);
        this.notificationService.error(err.error?.message || 'Failed to create export');
      }
    });
  }

  refreshDownloadUrl(exp: Export): void {
    this.refreshingUrl.set(exp.id);
    this.backupService.refreshExportUrl(this.clusterId, exp.id).subscribe({
      next: (updated) => {
        this.exports.update(list => list.map(e => e.id === updated.id ? updated : e));
        this.refreshingUrl.set(null);
        this.notificationService.success('Download link refreshed');
      },
      error: (err) => {
        this.refreshingUrl.set(null);
        this.notificationService.error(err.error?.message || 'Failed to refresh download link');
      }
    });
  }

  confirmDelete(exp: Export): void {
    this.exportToDelete = exp;
    this.showDeleteDialog.set(true);
  }

  closeDeleteDialog(): void {
    this.showDeleteDialog.set(false);
    this.exportToDelete = null;
  }

  deleteExport(): void {
    if (!this.exportToDelete) return;

    const exportId = this.exportToDelete.id;
    this.showDeleteDialog.set(false);
    this.deleting.set(exportId);

    this.backupService.deleteExport(this.clusterId, exportId).subscribe({
      next: () => {
        this.exports.update(list => list.filter(e => e.id !== exportId));
        this.deleting.set(null);
        this.exportToDelete = null;
        this.notificationService.success('Export deleted');
      },
      error: (err) => {
        this.deleting.set(null);
        this.notificationService.error(err.error?.message || 'Failed to delete export');
      }
    });
  }

  isUrlExpired(expiresAt: string | null): boolean {
    if (!expiresAt) return false;
    return new Date(expiresAt).getTime() < Date.now();
  }

  formatStatus(status: ExportStatus): string {
    switch (status) {
      case 'pending': return 'Pending';
      case 'in_progress': return 'Exporting';
      case 'completed': return 'Completed';
      case 'failed': return 'Failed';
      default: return status;
    }
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatDateTime(dateString: string | null | undefined): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getStatusDotClasses(status: ExportStatus): string {
    switch (status) {
      case 'completed': return 'bg-status-running';
      case 'in_progress':
      case 'pending': return 'bg-status-warning animate-pulse';
      case 'failed': return 'bg-status-error';
      default: return 'bg-status-stopped';
    }
  }

  getStatusBadgeClasses(status: ExportStatus): string {
    switch (status) {
      case 'completed':
        return 'badge-success';
      case 'in_progress':
      case 'pending':
        return 'badge-warning';
      case 'failed':
        return 'badge-error';
      default:
        return 'badge-muted';
    }
  }
}
