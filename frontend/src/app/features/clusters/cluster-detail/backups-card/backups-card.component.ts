import { Component, Input, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';
import { BackupService } from '../../../../core/services/backup.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Backup, BackupDeletionInfo, BackupStatus, BackupMetrics, BackupStep } from '../../../../core/models';
import { POLLING_INTERVALS } from '../../../../core/constants';
import {
  CardComponent,
  SpinnerComponent,
  StatusBadgeComponent,
  ConfirmDialogComponent
} from '../../../../shared/components';

@Component({
  selector: 'app-backups-card',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    SpinnerComponent,
    StatusBadgeComponent,
    ConfirmDialogComponent
  ],
  template: `
    <app-card title="Backups" description="Automated and manual backups with point-in-time recovery">
      <div class="space-y-6">
        <!-- Metrics Header -->
        @if (metrics()) {
          <div class="grid grid-cols-3 gap-4">
            <div class="p-4 rounded-lg bg-muted/50">
              <div class="text-2xl font-bold">{{ metrics()!.formattedTotalSize }}</div>
              <div class="text-sm text-muted-foreground">Total Storage</div>
            </div>
            <div class="p-4 rounded-lg bg-muted/50">
              <div class="text-2xl font-bold">{{ metrics()!.backupCount }}</div>
              <div class="text-sm text-muted-foreground">Backups</div>
            </div>
            <div class="p-4 rounded-lg bg-muted/50">
              <div class="text-2xl font-bold">{{ pitrWindow() }}</div>
              <div class="text-sm text-muted-foreground">PITR Available</div>
            </div>
          </div>

          <!-- Storage Trend Chart -->
          @if (metrics()!.storageTrend.length > 0) {
            <div class="h-24 flex items-end gap-0.5">
              @for (point of metrics()!.storageTrend.slice(-30); track point.date) {
                <div
                  class="flex-1 bg-primary/20 hover:bg-primary/40 transition-colors rounded-t min-h-[2px]"
                  [style.height.%]="getBarHeight(point.sizeBytes)"
                  [title]="point.date + ': ' + point.formattedSize"
                ></div>
              }
            </div>
          }
        }

        <!-- Action Button with Type Selector -->
        <div class="flex items-center justify-end gap-2">
          <select
            [(ngModel)]="selectedBackupType"
            [disabled]="!isClusterRunning || creating()"
            class="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <option value="incr">Incremental</option>
            <option value="diff">Differential</option>
            <option value="full">Full</option>
          </select>
          <button
            (click)="createBackup()"
            [disabled]="!isClusterRunning || creating()"
            class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-9 px-4"
          >
            @if (creating()) {
              <app-spinner size="sm" class="mr-2" />
              Creating...
            } @else {
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Create Backup
            }
          </button>
        </div>

        <!-- Loading state -->
        @if (loading()) {
          <div class="flex items-center justify-center py-8">
            <app-spinner size="md" />
          </div>
        } @else if (backups().length === 0) {
          <!-- Empty state -->
          <div class="text-center py-8 text-muted-foreground">
            <svg class="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
            </svg>
            <p class="font-medium">No backups yet</p>
            <p class="text-sm mt-1">Create your first backup to enable point-in-time recovery</p>
          </div>
        } @else {
          <!-- Backups list -->
          <div class="rounded-lg border divide-y">
            @for (backup of backups(); track backup.id) {
              <div class="p-4">
                <div class="flex items-start justify-between">
                  <div class="flex items-start gap-3">
                    <!-- Status indicator -->
                    <div [class]="getStatusClasses(backup.status)" class="w-2 h-2 rounded-full mt-2"></div>

                    <div class="flex-1">
                      <!-- Header with badges -->
                      <div class="flex items-center gap-2 flex-wrap">
                        <span class="font-medium">{{ formatBackupType(backup.type) }}</span>
                        @if (backup.backupType) {
                          <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400">
                            {{ formatPhysicalType(backup.backupType) }}
                          </span>
                        }
                        <span [class]="getStatusBadgeClasses(backup.status)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                          {{ formatStatus(backup.status) }}
                        </span>
                      </div>

                      <!-- Date and size -->
                      <div class="text-sm text-muted-foreground mt-1">
                        {{ formatDate(backup.createdAt) }}
                        @if (backup.formattedSize) {
                          <span class="mx-1">&middot;</span>
                          {{ backup.formattedSize }}
                        }
                      </div>

                      <!-- Recovery window for completed backups -->
                      @if (backup.status === 'completed' && backup.earliestRecoveryTime && backup.latestRecoveryTime) {
                        <div class="text-xs text-muted-foreground mt-1">
                          Recovery: {{ formatTime(backup.earliestRecoveryTime) }} - {{ formatTime(backup.latestRecoveryTime) }}
                          @if (backup.expiresAt) {
                            <span class="mx-1">&middot;</span>
                            {{ getExpiryText(backup.expiresAt) }}
                          } @else if (backup.retentionType === 'manual') {
                            <span class="mx-1">&middot;</span>
                            <span class="text-emerald-600 dark:text-emerald-400">Never expires</span>
                          }
                        </div>
                      }

                      <!-- Progress for in-progress backups -->
                      @if (backup.status === 'in_progress' || backup.status === 'pending') {
                        <div class="mt-2 space-y-1">
                          <div class="text-xs text-muted-foreground">
                            Step: {{ formatStep(backup.currentStep) }}
                          </div>
                          <div
                            class="w-full bg-muted rounded-full h-1.5"
                            role="progressbar"
                            [attr.aria-valuenow]="backup.progressPercent || 0"
                            aria-valuemin="0"
                            aria-valuemax="100"
                            [attr.aria-label]="'Backup progress: ' + (backup.progressPercent || 0) + '%'"
                          >
                            <div
                              class="bg-primary h-1.5 rounded-full transition-all duration-300"
                              [style.width.%]="backup.progressPercent || 0"
                            ></div>
                          </div>
                        </div>
                      }

                      <!-- Error message for failed backups -->
                      @if (backup.status === 'failed' && backup.errorMessage) {
                        <div class="text-xs text-red-500 mt-1">{{ backup.errorMessage }}</div>
                      }
                    </div>
                  </div>

                  <!-- Actions -->
                  <div class="flex items-center gap-2 ml-4">
                    @if (backup.status === 'completed') {
                      <button
                        (click)="openRestoreDialog(backup)"
                        class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-8 px-3"
                      >
                        <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                        Restore
                      </button>
                    }
                    @if (backup.status === 'in_progress') {
                      <app-spinner size="sm" />
                    } @else {
                      <button
                        (click)="confirmDelete(backup)"
                        [disabled]="deleting() === backup.id"
                        class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 text-muted-foreground hover:text-destructive h-8 w-8"
                      >
                        @if (deleting() === backup.id) {
                          <app-spinner size="sm" />
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
      @if (showDeleteDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-black/50" (click)="closeDeleteDialog()"></div>
          <div class="relative bg-background rounded-lg shadow-lg p-6 w-full max-w-lg mx-4 animate-in fade-in zoom-in-95">
            <h2 class="text-lg font-semibold mb-4">Delete Backup</h2>

            @if (loadingDeletionInfo()) {
              <div class="flex items-center justify-center py-8">
                <app-spinner size="md" />
              </div>
            } @else if (deleteErrorMessage()) {
              <div class="rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-4 mb-4">
                <p class="text-red-800 dark:text-red-200">{{ deleteErrorMessage() }}</p>
              </div>
              <div class="flex justify-end">
                <button
                  (click)="closeDeleteDialog()"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 border border-input bg-background hover:bg-accent"
                >
                  Close
                </button>
              </div>
            } @else if (deletionInfo()) {
              @if (deletionInfo()!.requiresConfirmation) {
                <div class="rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-4 mb-4">
                  <div class="flex items-start gap-3">
                    <svg class="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                    </svg>
                    <div>
                      <p class="font-medium text-amber-800 dark:text-amber-200">This will also delete dependent backups</p>
                      <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">{{ deletionInfo()!.warningMessage }}</p>
                    </div>
                  </div>
                </div>

                <div class="mb-4">
                  <p class="text-sm font-medium mb-2">Backups to be deleted:</p>
                  <div class="rounded-lg border divide-y max-h-48 overflow-y-auto">
                    <div class="p-3 flex justify-between items-center bg-muted/30">
                      <div>
                        <span class="font-medium">{{ formatPhysicalType(deletionInfo()!.backup.backupType) }}</span>
                        <span class="text-muted-foreground text-sm ml-2">(Primary)</span>
                      </div>
                      <span class="text-sm text-muted-foreground">{{ deletionInfo()!.backup.formattedSize }}</span>
                    </div>
                    @for (dep of deletionInfo()!.dependentBackups; track dep.id) {
                      <div class="p-3 flex justify-between items-center">
                        <div>
                          <span class="font-medium">{{ formatPhysicalType(dep.backupType) }}</span>
                          <span class="text-muted-foreground text-sm ml-2">{{ formatDate(dep.createdAt) }}</span>
                        </div>
                        <span class="text-sm text-muted-foreground">{{ dep.formattedSize }}</span>
                      </div>
                    }
                  </div>
                </div>

                <div class="flex justify-between items-center text-sm mb-6">
                  <span class="text-muted-foreground">Total to be deleted:</span>
                  <span class="font-medium">{{ deletionInfo()!.totalCount }} backup(s) · {{ deletionInfo()!.formattedTotalSize }}</span>
                </div>
              } @else {
                <p class="text-muted-foreground mb-6">
                  Are you sure you want to delete this {{ formatPhysicalType(deletionInfo()!.backup.backupType) }} backup?
                  This will free {{ deletionInfo()!.formattedTotalSize }} of storage.
                </p>
              }

              <div class="flex justify-end gap-3">
                <button
                  (click)="closeDeleteDialog()"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 border border-input bg-background hover:bg-accent"
                >
                  Cancel
                </button>
                <button
                  (click)="deleteBackup()"
                  [disabled]="deleting()"
                  class="inline-flex items-center justify-center rounded-md text-sm font-medium h-10 px-4 py-2 bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
                >
                  @if (deleting()) {
                    <app-spinner size="sm" class="mr-2" />
                    Deleting...
                  } @else {
                    Delete {{ deletionInfo()!.totalCount > 1 ? 'All' : '' }}
                  }
                </button>
              </div>
            }
          </div>
        </div>
      }

      <!-- Restore Confirmation Dialog -->
      @if (showRestoreDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-black/50" (click)="closeRestoreDialog()"></div>
          <div class="relative bg-background rounded-lg shadow-lg p-6 w-full max-w-lg mx-4 animate-in fade-in zoom-in-95">
            <h2 class="text-lg font-semibold mb-2">Restore from Backup</h2>

            <div class="rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-4 mb-6">
              <div class="flex items-start gap-3">
                <svg class="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <div>
                  <p class="font-medium text-amber-800 dark:text-amber-200">A new cluster will be created</p>
                  <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">
                    The original cluster will not be modified.
                  </p>
                </div>
              </div>
            </div>

            <div class="space-y-3 mb-6">
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">New cluster name:</span>
                <span class="font-mono font-medium">{{ getRestoredClusterName() }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">Recovery point:</span>
                <span class="font-medium">{{ formatDateTime(selectedBackup()?.completedAt || selectedBackup()?.createdAt) }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">Source backup:</span>
                <span class="font-medium">{{ formatBackupType(selectedBackup()?.type!) }} ({{ formatPhysicalType(selectedBackup()?.backupType!) }})</span>
              </div>
            </div>

            <div class="flex justify-end gap-3">
              <button
                (click)="closeRestoreDialog()"
                class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-10 px-4 py-2"
              >
                Cancel
              </button>
              <button
                (click)="restoreBackup()"
                [disabled]="restoring()"
                class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
              >
                @if (restoring()) {
                  <app-spinner size="sm" class="mr-2" />
                  Restoring...
                } @else {
                  Restore
                }
              </button>
            </div>
          </div>
        </div>
      }

    </app-card>
  `
})
export class BackupsCardComponent implements OnInit, OnDestroy {
  @Input({ required: true }) clusterId!: string;
  @Input() clusterSlug: string = '';
  @Input() isClusterRunning: boolean = false;

  loading = signal(true);
  creating = signal(false);
  deleting = signal<string | null>(null);
  restoring = signal(false);
  backups = signal<Backup[]>([]);
  metrics = signal<BackupMetrics | null>(null);

  showDeleteDialog = signal(false);
  deleteErrorMessage = signal<string | null>(null);
  deletionInfo = signal<BackupDeletionInfo | null>(null);
  loadingDeletionInfo = signal(false);
  showRestoreDialog = signal(false);
  selectedBackup = signal<Backup | null>(null);

  // Backup type selector
  selectedBackupType: 'full' | 'diff' | 'incr' = 'incr';

  private backupToDelete: Backup | null = null;
  private pollingSubscription?: Subscription;
  private maxStorageSize = 0;

  constructor(
    private backupService: BackupService,
    private notificationService: NotificationService
  ) {}

  pitrWindow = computed(() => {
    const m = this.metrics();
    if (!m || !m.earliestPitrTime || !m.latestPitrTime) return 'N/A';

    const earliest = new Date(m.earliestPitrTime);
    const latest = new Date(m.latestPitrTime);
    const diffMs = latest.getTime() - earliest.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays >= 1) return `${diffDays} days`;
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHours >= 1) return `${diffHours} hours`;
    const diffMins = Math.floor(diffMs / (1000 * 60));
    return `${diffMins} mins`;
  });

  ngOnInit(): void {
    this.loadBackups();
    this.loadMetrics();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }

  private loadBackups(): void {
    this.backupService.getBackups(this.clusterId).subscribe({
      next: (response) => {
        // API excludes deleted backups by default
        this.backups.set(response.backups);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.notificationService.error(err.error?.message || 'Failed to load backups');
      }
    });
  }

  private loadMetrics(): void {
    this.backupService.getBackupMetrics(this.clusterId).subscribe({
      next: (metrics) => {
        this.metrics.set(metrics);
        this.maxStorageSize = Math.max(...metrics.storageTrend.map(p => p.sizeBytes), 1);
      },
      error: () => {
        // Silently fail - metrics are non-critical
      }
    });
  }

  private startPolling(): void {
    this.pollingSubscription = interval(POLLING_INTERVALS.BACKUP_STATUS).subscribe(() => {
      const hasInProgress = this.backups().some(b => b.status === 'in_progress' || b.status === 'pending');
      if (hasInProgress) {
        this.loadBackups();
      }
    });
  }

  getBarHeight(sizeBytes: number): number {
    if (this.maxStorageSize === 0) return 0;
    return Math.max((sizeBytes / this.maxStorageSize) * 100, 2);
  }

  createBackup(): void {
    this.creating.set(true);
    this.backupService.createBackup(this.clusterId, this.selectedBackupType).subscribe({
      next: (backup) => {
        this.backups.update(list => [backup, ...list]);
        this.creating.set(false);
        this.loadBackups();
        const typeLabel = this.formatPhysicalType(this.selectedBackupType);
        this.notificationService.success(`${typeLabel} backup started`);
      },
      error: (err) => {
        this.creating.set(false);
        this.notificationService.error(err.error?.message || 'Failed to create backup');
      }
    });
  }

  confirmDelete(backup: Backup): void {
    this.backupToDelete = backup;
    this.deleteErrorMessage.set(null);
    this.deletionInfo.set(null);
    this.loadingDeletionInfo.set(true);
    this.showDeleteDialog.set(true);

    // Fetch deletion info to show what will be deleted
    this.backupService.getBackupDeletionInfo(this.clusterId, backup.id).subscribe({
      next: (info) => {
        this.deletionInfo.set(info);
        this.loadingDeletionInfo.set(false);
      },
      error: (err) => {
        this.loadingDeletionInfo.set(false);
        this.deleteErrorMessage.set(err.error?.message || 'Failed to load deletion info');
      }
    });
  }

  closeDeleteDialog(): void {
    this.showDeleteDialog.set(false);
    this.deleteErrorMessage.set(null);
    this.deletionInfo.set(null);
    this.backupToDelete = null;
  }

  deleteBackup(): void {
    if (!this.backupToDelete) return;

    const backupId = this.backupToDelete.id;
    const info = this.deletionInfo();
    this.deleting.set(backupId);

    // Always pass confirm=true since user confirmed via dialog
    this.backupService.deleteBackup(this.clusterId, backupId, true).subscribe({
      next: () => {
        // Remove the primary backup and all dependents from the list
        const idsToRemove = new Set([backupId]);
        if (info?.dependentBackups) {
          info.dependentBackups.forEach(b => idsToRemove.add(b.id));
        }
        this.backups.update(list => list.filter(b => !idsToRemove.has(b.id)));

        this.deleting.set(null);
        this.backupToDelete = null;
        this.showDeleteDialog.set(false);
        this.deletionInfo.set(null);
        this.loadMetrics();

        const count = idsToRemove.size;
        this.notificationService.success(
          count > 1
            ? `${count} backups deleted successfully`
            : 'Backup deleted successfully'
        );
      },
      error: (err) => {
        this.deleting.set(null);
        const message = err.error?.message || err.message || 'Failed to delete backup';
        this.deleteErrorMessage.set(message);
      }
    });
  }

  openRestoreDialog(backup: Backup): void {
    this.selectedBackup.set(backup);
    this.showRestoreDialog.set(true);
  }

  closeRestoreDialog(): void {
    this.showRestoreDialog.set(false);
  }

  restoreBackup(): void {
    const backup = this.selectedBackup();
    if (!backup) return;

    this.restoring.set(true);

    const request = {
      createNewCluster: true,
      newClusterName: this.getRestoredClusterName()
    };

    this.backupService.restoreBackup(this.clusterId, backup.id, request).subscribe({
      next: () => {
        this.restoring.set(false);
        this.showRestoreDialog.set(false);
        this.notificationService.success('Restore job started. A new cluster is being created.');
      },
      error: (err) => {
        this.restoring.set(false);
        this.notificationService.error(err.error?.message || 'Failed to start restore');
      }
    });
  }

  getRestoredClusterName(): string {
    const today = new Date();
    const dateStr = today.toISOString().slice(0, 10).replace(/-/g, '');
    const suffix = `-restored-${dateStr}`;
    // Strip any existing "-restored-YYYYMMDD" and everything after it
    const baseName = (this.clusterSlug || 'cluster').replace(/-restored-\d{8}.*$/, '');
    const maxBaseLength = 50 - suffix.length;
    return `${baseName.slice(0, maxBaseLength)}${suffix}`;
  }

  formatBackupType(type: string): string {
    switch (type) {
      case 'manual': return 'Manual Backup';
      case 'scheduled_daily': return 'Daily Backup';
      case 'scheduled_weekly': return 'Weekly Backup';
      case 'scheduled_monthly': return 'Monthly Backup';
      default: return type;
    }
  }

  formatPhysicalType(type: string | null): string {
    switch (type) {
      case 'full': return 'Full';
      case 'diff': return 'Differential';
      case 'incr': return 'Incremental';
      default: return type || '';
    }
  }

  formatStatus(status: BackupStatus): string {
    switch (status) {
      case 'pending': return 'Pending';
      case 'in_progress': return 'In Progress';
      case 'completed': return 'Completed';
      case 'failed': return 'Failed';
      case 'expired': return 'Expired';
      case 'deleted': return 'Deleted';
      default: return status;
    }
  }

  formatStep(step: BackupStep | null): string {
    switch (step) {
      case 'pending': return 'Waiting to start...';
      case 'preparing': return 'Preparing backup...';
      case 'backing_up': return 'Backing up database...';
      case 'uploading': return 'Uploading to storage...';
      case 'verifying': return 'Verifying backup...';
      case 'completed': return 'Complete';
      case 'failed': return 'Failed';
      default: return step || 'Processing...';
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

  formatTime(dateString: string | null | undefined): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleTimeString('en-US', {
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

  getExpiryText(expiresAt: string): string {
    const expires = new Date(expiresAt);
    const now = new Date();
    const diffMs = expires.getTime() - now.getTime();

    if (diffMs <= 0) return 'Expired';

    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays === 1) return 'Expires in 1 day';
    return `Expires in ${diffDays} days`;
  }

  getStatusClasses(status: BackupStatus): string {
    switch (status) {
      case 'completed': return 'bg-emerald-500';
      case 'in_progress':
      case 'pending': return 'bg-amber-500 animate-pulse';
      case 'failed': return 'bg-red-500';
      default: return 'bg-gray-400';
    }
  }

  getStatusBadgeClasses(status: BackupStatus): string {
    switch (status) {
      case 'completed':
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400';
      case 'in_progress':
      case 'pending':
        return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400';
      case 'failed':
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400';
    }
  }
}
