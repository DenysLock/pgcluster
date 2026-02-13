import { Component, Input, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { BackupService } from '../../../../core/services/backup.service';
import { ClusterService } from '../../../../core/services/cluster.service';
import { AdminService } from '../../../../core/services/admin.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  Backup,
  BackupDeletionInfo,
  BackupMetrics,
  BackupStatus,
  BackupStep,
  ClusterNode,
  Location,
  PitrRestoreRequest,
  PitrWindowResponse,
  ServerType,
  ServerTypesResponse
} from '../../../../core/models';
import { POLLING_INTERVALS } from '../../../../core/constants';
import { PitrPickerComponent } from './pitr-picker.component';

@Component({
  selector: 'app-backups-card',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    PitrPickerComponent
  ],
  template: `
    <div class="card">
      <div class="card-header">Backups</div>
      <div class="space-y-6">
        @if (metrics()) {
          <div class="grid grid-cols-3 gap-4">
            <div class="p-4 bg-bg-tertiary border border-border rounded">
              <div class="text-lg font-bold text-foreground">{{ metrics()!.formattedTotalSize }}</div>
              <div class="text-xs uppercase tracking-wider text-muted-foreground">Total Storage</div>
            </div>
            <div class="p-4 bg-bg-tertiary border border-border rounded">
              <div class="text-lg font-bold text-foreground">{{ metrics()!.backupCount }}</div>
              <div class="text-xs uppercase tracking-wider text-muted-foreground">Backups</div>
            </div>
            <div class="p-4 bg-bg-tertiary border border-border rounded">
              <div class="text-lg font-bold text-foreground">{{ pitrWindowLabel() }}</div>
              <div class="text-xs uppercase tracking-wider text-muted-foreground">PITR Available</div>
            </div>
          </div>

          @if (metrics()!.storageTrend.length > 0) {
            <div class="h-24 flex items-end gap-0.5">
              @for (point of metrics()!.storageTrend.slice(-30); track point.date) {
                <div
                  class="flex-1 bg-primary/20 hover:bg-primary/40 transition-colors min-h-[2px] rounded-t"
                  [style.height.%]="getBarHeight(point.sizeBytes)"
                  [title]="point.date + ': ' + point.formattedSize"
                ></div>
              }
            </div>
          }
        }

        @if (!isAdmin) {
          <div class="flex items-center justify-end gap-2 flex-wrap">
            @if (pitrWindow()?.available) {
              <button
                (click)="openPitrRestoreDialog()"
                [disabled]="restoring() || creating()"
                class="btn-secondary h-9 px-4 whitespace-nowrap"
                type="button"
              >
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 4v5h5M3.05 13a9 9 0 101.49-5.36L3 9m18 11v-5h-5" />
                </svg>
                Restore from PITR
              </button>
            }

            <select
              [(ngModel)]="selectedBackupType"
              [disabled]="!isClusterRunning || creating()"
              class="select h-9 px-3 py-1 text-sm"
            >
              <option value="incr">Incremental</option>
              <option value="diff">Differential</option>
              <option value="full">Full</option>
            </select>
            <button
              (click)="createBackup()"
              [disabled]="!isClusterRunning || creating()"
              class="btn-primary h-9 px-6 whitespace-nowrap"
              type="button"
            >
              @if (creating()) {
                <span class="spinner w-4 h-4 mr-2"></span>
                Creating...
              } @else {
                <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
                Create Backup
              }
            </button>
          </div>
        }

        @if (loading()) {
          <div class="flex items-center justify-center py-8">
            <span class="spinner w-6 h-6"></span>
          </div>
        } @else if (backups().length === 0) {
          <div class="text-center py-8 text-muted-foreground">
            <svg class="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
            </svg>
            <p class="font-semibold text-foreground">No backups yet</p>
            <p class="text-sm mt-1">Create your first backup to enable point-in-time recovery</p>
          </div>
        } @else {
          <div class="border border-border divide-y divide-border">
            @for (backup of backups(); track backup.id) {
              <div class="p-4 hover:bg-bg-tertiary transition-colors">
                <div class="flex items-start justify-between">
                  <div class="flex items-start gap-3">
                    <div [class]="getStatusClasses(backup.status)" class="w-2 h-2 mt-2"></div>

                    <div class="flex-1">
                      <div class="flex items-center gap-2 flex-wrap">
                        <span class="text-sm font-semibold text-foreground">{{ formatBackupType(backup.type) }}</span>
                        @if (backup.backupType) {
                          <span class="badge badge-info">
                            {{ formatPhysicalType(backup.backupType) }}
                          </span>
                        }
                        <span class="badge" [ngClass]="getStatusBadgeClasses(backup.status)">
                          {{ formatStatus(backup.status) }}
                        </span>
                      </div>

                      <div class="text-sm text-muted-foreground mt-1">
                        {{ formatDate(backup.createdAt) }}
                        @if (backup.formattedSize) {
                          <span class="mx-1">&middot;</span>
                          {{ backup.formattedSize }}
                        }
                      </div>

                      @if (backup.status === 'completed' && backup.earliestRecoveryTime && backup.latestRecoveryTime) {
                        <div class="text-xs text-muted-foreground mt-1">
                          Recovery: {{ formatTime(backup.earliestRecoveryTime) }} - {{ formatTime(backup.latestRecoveryTime) }}
                          @if (backup.expiresAt) {
                            <span class="mx-1">&middot;</span>
                            {{ getExpiryText(backup.expiresAt) }}
                          } @else if (backup.retentionType === 'manual') {
                            <span class="mx-1">&middot;</span>
                            <span class="text-green-600">Never expires</span>
                          }
                        </div>
                      }

                      @if (backup.status === 'in_progress' || backup.status === 'pending') {
                        <div class="mt-2 space-y-1">
                          <div class="text-xs text-muted-foreground">
                            Step: {{ formatStep(backup.currentStep) }}
                          </div>
                          <div
                            class="w-full bg-bg-tertiary h-1.5 rounded"
                            role="progressbar"
                            [attr.aria-valuenow]="backup.progressPercent || 0"
                            aria-valuemin="0"
                            aria-valuemax="100"
                            [attr.aria-label]="'Backup progress: ' + (backup.progressPercent || 0) + '%'"
                          >
                            <div
                              class="bg-primary h-1.5 transition-all duration-300 rounded"
                              [style.width.%]="backup.progressPercent || 0"
                            ></div>
                          </div>
                        </div>
                      }

                      @if (backup.status === 'failed' && backup.errorMessage) {
                        <div class="text-xs text-status-error mt-1">{{ backup.errorMessage }}</div>
                      }
                    </div>
                  </div>

                  <div class="flex items-center gap-2 ml-4">
                    @if (backup.status === 'completed' && !isAdmin) {
                      <button
                        (click)="openRestoreDialog(backup)"
                        class="btn-secondary h-8 px-3 text-sm"
                        type="button"
                      >
                        <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                        Restore
                      </button>
                    }
                    @if (backup.status === 'in_progress') {
                      <span class="spinner w-4 h-4"></span>
                    } @else {
                      <button
                        (click)="confirmDelete(backup)"
                        [disabled]="deleting() === backup.id"
                        class="text-muted-foreground hover:text-status-error transition-colors h-8 w-8 flex items-center justify-center disabled:opacity-50"
                        type="button"
                      >
                        @if (deleting() === backup.id) {
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

      @if (showDeleteDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-slate-900/50" (click)="closeDeleteDialog()"></div>
          <div class="relative bg-white border border-border shadow-lg rounded-lg p-6 w-full max-w-lg mx-4">
            <h2 class="text-lg font-semibold text-foreground mb-4">Delete Backup</h2>

            @if (loadingDeletionInfo()) {
              <div class="flex items-center justify-center py-8">
                <span class="spinner w-6 h-6"></span>
              </div>
            } @else if (deleteErrorMessage()) {
              <div class="bg-status-error/10 border border-status-error p-4 mb-4">
                <p class="text-status-error">{{ deleteErrorMessage() }}</p>
              </div>
              <div class="flex justify-end">
                <button
                  (click)="closeDeleteDialog()"
                  class="btn-secondary"
                  type="button"
                >
                  Close
                </button>
              </div>
            } @else if (deletionInfo()) {
              @if (deletionInfo()!.requiresConfirmation) {
                <div class="bg-status-warning/10 border border-status-warning p-4 mb-4">
                  <div class="flex items-start gap-3">
                    <svg class="w-5 h-5 text-status-warning shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                    </svg>
                    <div>
                      <p class="font-semibold text-status-warning">This will also delete dependent backups</p>
                      <p class="text-sm text-status-warning/80 mt-1">{{ deletionInfo()!.warningMessage }}</p>
                    </div>
                  </div>
                </div>

                <div class="mb-4">
                  <p class="text-sm font-semibold text-foreground mb-2">Backups to be deleted:</p>
                  <div class="border border-border divide-y divide-border max-h-48 overflow-y-auto">
                    <div class="p-3 flex justify-between items-center bg-bg-tertiary">
                      <div>
                        <span class="font-medium text-foreground">{{ formatPhysicalType(deletionInfo()!.backup.backupType) }}</span>
                        <span class="text-muted-foreground text-sm ml-2">(Primary)</span>
                      </div>
                      <span class="text-sm text-muted-foreground">{{ deletionInfo()!.backup.formattedSize }}</span>
                    </div>
                    @for (dep of deletionInfo()!.dependentBackups; track dep.id) {
                      <div class="p-3 flex justify-between items-center">
                        <div>
                          <span class="font-medium text-foreground">{{ formatPhysicalType(dep.backupType) }}</span>
                          <span class="text-muted-foreground text-sm ml-2">{{ formatDate(dep.createdAt) }}</span>
                        </div>
                        <span class="text-sm text-muted-foreground">{{ dep.formattedSize }}</span>
                      </div>
                    }
                  </div>
                </div>

                <div class="flex justify-between items-center text-sm mb-6">
                  <span class="text-muted-foreground">Total to be deleted:</span>
                  <span class="font-semibold text-foreground">{{ deletionInfo()!.totalCount }} backup(s) · {{ deletionInfo()!.formattedTotalSize }}</span>
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
                  class="btn-secondary"
                  type="button"
                >
                  Cancel
                </button>
                <button
                  (click)="deleteBackup()"
                  [disabled]="deleting()"
                  class="btn-danger"
                  type="button"
                >
                  @if (deleting()) {
                    <span class="spinner w-4 h-4 mr-2 border-status-error"></span>
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

      @if (showRestoreDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-slate-900/50" (click)="closeRestoreDialog()"></div>
          <div class="relative bg-white border border-border shadow-lg rounded-lg p-6 w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
            <h2 class="text-lg font-semibold text-foreground mb-4">Restore from Backup</h2>

            <div class="bg-status-warning/10 border border-status-warning p-4 mb-6">
              <div class="flex items-start gap-3">
                <svg class="w-5 h-5 text-status-warning shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <div>
                  <p class="font-semibold text-status-warning">A new cluster will be created</p>
                  <p class="text-sm text-status-warning/80 mt-1">
                    The original cluster will not be modified.
                  </p>
                </div>
              </div>
            </div>
            <div class="space-y-3 mb-4">
              <label class="label">Cluster Mode</label>
              <div class="flex gap-3">
                <button
                  type="button"
                  (click)="toggleHaMode(false)"
                  [ngClass]="!haMode() ? 'flex-1 p-3 border rounded text-left transition-all border-primary bg-primary/10' : 'flex-1 p-3 border rounded text-left transition-all border-border hover:border-muted-foreground'"
                >
                  <div class="text-sm font-semibold text-foreground">Single Node</div>
                  <div class="text-xs text-muted-foreground mt-1">1 node, lower cost, no HA</div>
                </button>
                <button
                  type="button"
                  (click)="toggleHaMode(true)"
                  [ngClass]="haMode() ? 'flex-1 p-3 border rounded text-left transition-all border-primary bg-primary/10' : 'flex-1 p-3 border rounded text-left transition-all border-border hover:border-muted-foreground'"
                >
                  <div class="text-sm font-semibold text-foreground">High Availability</div>
                  <div class="text-xs text-muted-foreground mt-1">3 nodes, auto-failover</div>
                </button>
              </div>
            </div>

            <div class="space-y-3 mb-4">
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
                <div class="flex gap-2 mb-3">
                  <button
                    type="button"
                    (click)="selectCategory('shared')"
                    class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border rounded transition-colors"
                    [class.bg-primary]="selectedCategory() === 'shared'"
                    [class.text-white]="selectedCategory() === 'shared'"
                    [class.border-primary]="selectedCategory() === 'shared'"
                    [class.bg-transparent]="selectedCategory() !== 'shared'"
                    [class.text-muted-foreground]="selectedCategory() !== 'shared'"
                    [class.border-border]="selectedCategory() !== 'shared'"
                  >
                    Shared vCPU
                  </button>
                  <button
                    type="button"
                    (click)="selectCategory('dedicated')"
                    class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border rounded transition-colors"
                    [class.bg-primary]="selectedCategory() === 'dedicated'"
                    [class.text-white]="selectedCategory() === 'dedicated'"
                    [class.border-primary]="selectedCategory() === 'dedicated'"
                    [class.bg-transparent]="selectedCategory() !== 'dedicated'"
                    [class.text-muted-foreground]="selectedCategory() !== 'dedicated'"
                    [class.border-border]="selectedCategory() !== 'dedicated'"
                  >
                    Dedicated vCPU
                  </button>
                </div>

                <div class="grid grid-cols-4 gap-2">
                  @for (type of currentServerTypes(); track type.name) {
                    <button
                      type="button"
                      (click)="selectServerType(type)"
                      [ngClass]="getServerTypeClass(type)"
                      [disabled]="!isServerTypeAvailable(type)"
                    >
                      <div class="font-semibold text-foreground text-sm">{{ type.name }}</div>
                      <div class="text-xs text-muted-foreground space-y-0.5 mt-1">
                        <div>{{ type.cores }} vCPU</div>
                        <div>{{ type.memory }} GB</div>
                      </div>
                    </button>
                  }
                </div>
              }
            </div>

            <div class="space-y-2 mb-4">
              <label class="label">Cluster Name</label>
              <input
                type="text"
                [value]="restoreClusterName()"
                (input)="restoreClusterName.set($any($event.target).value)"
                class="input"
                placeholder="my-restored-cluster"
              />
              <p class="text-xs text-muted-foreground">
                Use lowercase letters, numbers, and hyphens only.
              </p>
            </div>

            <div class="space-y-4 mb-6">
              <label class="label">Node {{ haMode() ? 'Locations' : 'Location' }}</label>
              <p class="text-xs text-muted-foreground -mt-2">
                Select a region for {{ haMode() ? 'each node' : 'your node' }}. Grayed buttons are unavailable for the selected server type.
              </p>

              @if (locationsLoading() || serverTypesLoading()) {
                <div class="flex items-center gap-2 text-muted-foreground py-4">
                  <span class="spinner w-4 h-4"></span>
                  <span>Loading locations...</span>
                </div>
              } @else {
                <div class="space-y-4">
                  @for (nodeIndex of (haMode() ? [0, 1, 2] : [0]); track nodeIndex) {
                    <div class="space-y-2">
                      <span class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        {{ haMode() ? 'Node ' + (nodeIndex + 1) : 'Node' }}
                      </span>
                      <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
                        @for (loc of filteredLocations(); track loc.id) {
                          <button
                            type="button"
                            (click)="selectRestoreLocation(nodeIndex, loc.id)"
                            [ngClass]="getRestoreLocationClass(nodeIndex, loc)"
                            [disabled]="!loc.available"
                          >
                            <span>{{ loc.flag }}</span>
                            <span class="text-xs truncate">{{ loc.city }}</span>
                          </button>
                        }
                      </div>
                    </div>
                  }
                </div>
              }
            </div>

            @if (hasUnavailableRestoreSelections()) {
              <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 text-sm mb-4">
                One or more selected regions are no longer available. Please select different regions.
              </div>
            }

            <div class="space-y-3 mb-6 border-t border-border pt-4">
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">PostgreSQL version:</span>
                <span class="font-semibold text-foreground">{{ sourcePostgresVersion }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">Recovery point:</span>
                <span class="font-semibold text-foreground">{{ formatDateTime(selectedBackup()?.completedAt || selectedBackup()?.createdAt) }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">Source backup:</span>
                <span class="font-semibold text-foreground">{{ formatBackupType(selectedBackup()?.type!) }} ({{ formatPhysicalType(selectedBackup()?.backupType!) }})</span>
              </div>
            </div>

            <div class="flex justify-end gap-3">
              <button
                (click)="closeRestoreDialog()"
                class="btn-secondary"
                type="button"
              >
                Cancel
              </button>
              <button
                type="button"
                (click)="onRestoreClick()"
                class="btn-primary"
              >
                @if (restoring()) {
                  <span class="spinner w-4 h-4 mr-2"></span>
                  Restoring...
                } @else {
                  Restore
                }
              </button>
            </div>
          </div>
        </div>
      }

      @if (showPitrDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center">
          <div class="fixed inset-0 bg-slate-900/50" (click)="closePitrRestoreDialog()"></div>
          <div class="relative bg-white border border-border shadow-lg rounded-lg p-6 w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto">
            <h2 class="text-lg font-semibold text-foreground mb-4">Restore from PITR</h2>

            <div class="bg-status-warning/10 border border-status-warning p-4 mb-6">
              <div class="flex items-start gap-3">
                <svg class="w-5 h-5 text-status-warning shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <div>
                  <p class="font-semibold text-status-warning">A new cluster will be created</p>
                  <p class="text-sm text-status-warning/80 mt-1">Choose a UTC recovery point, then configure the target cluster.</p>
                </div>
              </div>
            </div>

            <div class="flex items-center gap-2 mb-6">
              <div class="flex items-center gap-2">
                <span [class]="pitrStep() === 1 ? 'w-6 h-6 rounded-full bg-primary text-white text-xs flex items-center justify-center' : 'w-6 h-6 rounded-full bg-muted text-muted-foreground text-xs flex items-center justify-center'">1</span>
                <span [class]="pitrStep() === 1 ? 'text-sm font-semibold text-foreground' : 'text-sm text-muted-foreground'">Select PITR Time</span>
              </div>
              <div class="flex-1 h-px bg-border"></div>
              <div class="flex items-center gap-2">
                <span [class]="pitrStep() === 2 ? 'w-6 h-6 rounded-full bg-primary text-white text-xs flex items-center justify-center' : 'w-6 h-6 rounded-full bg-muted text-muted-foreground text-xs flex items-center justify-center'">2</span>
                <span [class]="pitrStep() === 2 ? 'text-sm font-semibold text-foreground' : 'text-sm text-muted-foreground'">Configure Cluster</span>
              </div>
            </div>

            @if (pitrStep() === 1) {
              @if (pitrWindowLoading()) {
                <div class="flex items-center justify-center py-10">
                  <span class="spinner w-6 h-6"></span>
                </div>
              } @else if (!pitrWindow()?.available) {
                <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 text-sm">
                  PITR is currently unavailable for this cluster.
                </div>
                <div class="flex justify-end gap-3 mt-6">
                  <button (click)="closePitrRestoreDialog()" class="btn-secondary" type="button">Close</button>
                </div>
              } @else {
                <app-pitr-picker
                  [earliestTime]="pitrWindow()!.earliestPitrTime"
                  [latestTime]="pitrWindow()!.latestPitrTime"
                  [initialTime]="selectedPitrTime()"
                  (timeSelected)="onPitrTimeSelected($event)"
                />

                <div class="space-y-2 mt-4">
                  <label class="label">Selected PITR time (UTC ISO)</label>
                  <input class="input font-mono text-xs" type="text" [value]="selectedPitrTime()" readonly />
                </div>

                <div class="flex justify-end gap-3 mt-6">
                  <button (click)="closePitrRestoreDialog()" class="btn-secondary" type="button">Cancel</button>
                  <button
                    (click)="goToPitrStep2()"
                    [disabled]="!canContinuePitrStep()"
                    class="btn-primary"
                    type="button"
                  >
                    Continue
                  </button>
                </div>
              }
            } @else {
              <div class="space-y-3 mb-4">
                <label class="label">Cluster Mode</label>
                <div class="flex gap-3">
                  <button
                    type="button"
                    (click)="toggleHaMode(false)"
                    [ngClass]="!haMode() ? 'flex-1 p-3 border rounded text-left transition-all border-primary bg-primary/10' : 'flex-1 p-3 border rounded text-left transition-all border-border hover:border-muted-foreground'"
                  >
                    <div class="text-sm font-semibold text-foreground">Single Node</div>
                    <div class="text-xs text-muted-foreground mt-1">1 node, lower cost, no HA</div>
                  </button>
                  <button
                    type="button"
                    (click)="toggleHaMode(true)"
                    [ngClass]="haMode() ? 'flex-1 p-3 border rounded text-left transition-all border-primary bg-primary/10' : 'flex-1 p-3 border rounded text-left transition-all border-border hover:border-muted-foreground'"
                  >
                    <div class="text-sm font-semibold text-foreground">High Availability</div>
                    <div class="text-xs text-muted-foreground mt-1">3 nodes, auto-failover</div>
                  </button>
                </div>
              </div>

              <div class="space-y-3 mb-4">
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
                  <div class="flex gap-2 mb-3">
                    <button
                      type="button"
                      (click)="selectCategory('shared')"
                      class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border rounded transition-colors"
                      [class.bg-primary]="selectedCategory() === 'shared'"
                      [class.text-white]="selectedCategory() === 'shared'"
                      [class.border-primary]="selectedCategory() === 'shared'"
                      [class.bg-transparent]="selectedCategory() !== 'shared'"
                      [class.text-muted-foreground]="selectedCategory() !== 'shared'"
                      [class.border-border]="selectedCategory() !== 'shared'"
                    >
                      Shared vCPU
                    </button>
                    <button
                      type="button"
                      (click)="selectCategory('dedicated')"
                      class="px-4 py-2 text-sm font-semibold uppercase tracking-wider border rounded transition-colors"
                      [class.bg-primary]="selectedCategory() === 'dedicated'"
                      [class.text-white]="selectedCategory() === 'dedicated'"
                      [class.border-primary]="selectedCategory() === 'dedicated'"
                      [class.bg-transparent]="selectedCategory() !== 'dedicated'"
                      [class.text-muted-foreground]="selectedCategory() !== 'dedicated'"
                      [class.border-border]="selectedCategory() !== 'dedicated'"
                    >
                      Dedicated vCPU
                    </button>
                  </div>

                  <div class="grid grid-cols-4 gap-2">
                    @for (type of currentServerTypes(); track type.name) {
                      <button
                        type="button"
                        (click)="selectServerType(type)"
                        [ngClass]="getServerTypeClass(type)"
                        [disabled]="!isServerTypeAvailable(type)"
                      >
                        <div class="font-semibold text-foreground text-sm">{{ type.name }}</div>
                        <div class="text-xs text-muted-foreground space-y-0.5 mt-1">
                          <div>{{ type.cores }} vCPU</div>
                          <div>{{ type.memory }} GB</div>
                        </div>
                      </button>
                    }
                  </div>
                }
              </div>

              <div class="space-y-2 mb-4">
                <label class="label">Cluster Name</label>
                <input
                  type="text"
                  [value]="restoreClusterName()"
                  (input)="restoreClusterName.set($any($event.target).value)"
                  class="input"
                  placeholder="my-restored-cluster"
                />
                <p class="text-xs text-muted-foreground">Use lowercase letters, numbers, and hyphens only.</p>
              </div>

              <div class="space-y-4 mb-6">
                <label class="label">Node {{ haMode() ? 'Locations' : 'Location' }}</label>
                <p class="text-xs text-muted-foreground -mt-2">
                  Select a region for {{ haMode() ? 'each node' : 'your node' }}. Grayed buttons are unavailable for the selected server type.
                </p>

                @if (locationsLoading() || serverTypesLoading()) {
                  <div class="flex items-center gap-2 text-muted-foreground py-4">
                    <span class="spinner w-4 h-4"></span>
                    <span>Loading locations...</span>
                  </div>
                } @else {
                  <div class="space-y-4">
                    @for (nodeIndex of (haMode() ? [0, 1, 2] : [0]); track nodeIndex) {
                      <div class="space-y-2">
                        <span class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                          {{ haMode() ? 'Node ' + (nodeIndex + 1) : 'Node' }}
                        </span>
                        <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
                          @for (loc of filteredLocations(); track loc.id) {
                            <button
                              type="button"
                              (click)="selectRestoreLocation(nodeIndex, loc.id)"
                              [ngClass]="getRestoreLocationClass(nodeIndex, loc)"
                              [disabled]="!loc.available"
                            >
                              <span>{{ loc.flag }}</span>
                              <span class="text-xs truncate">{{ loc.city }}</span>
                            </button>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>

              @if (hasUnavailableRestoreSelections()) {
                <div class="bg-status-error/10 border border-status-error text-status-error px-4 py-3 text-sm mb-4">
                  One or more selected regions are no longer available. Please select different regions.
                </div>
              }

              <div class="space-y-3 mb-6 border-t border-border pt-4">
                <div class="flex justify-between text-sm">
                  <span class="text-muted-foreground">PostgreSQL version:</span>
                  <span class="font-semibold text-foreground">{{ sourcePostgresVersion }}</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-muted-foreground">Recovery point (UTC):</span>
                  <span class="font-semibold text-foreground">{{ formatUtcIso(selectedPitrTime()) }}</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-muted-foreground">Source backup:</span>
                  <span class="font-semibold text-foreground">Auto-selected by server</span>
                </div>
              </div>

              <div class="flex justify-end gap-3">
                <button (click)="backToPitrStep1()" class="btn-secondary" type="button">Back</button>
                <button (click)="onPitrRestoreClick()" class="btn-primary" type="button">
                  @if (restoring()) {
                    <span class="spinner w-4 h-4 mr-2"></span>
                    Restoring...
                  } @else {
                    Restore
                  }
                </button>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class BackupsCardComponent implements OnInit, OnDestroy {
  @Input({ required: true }) clusterId!: string;
  @Input() clusterSlug: string = '';
  @Input() isClusterRunning: boolean = false;
  @Input() clusterNodes: ClusterNode[] = [];
  @Input() sourceNodeSize: string = 'cx23';
  @Input() sourcePostgresVersion: string = '16';
  @Input() isAdmin: boolean = false;

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

  showPitrDialog = signal(false);
  pitrStep = signal<1 | 2>(1);
  pitrWindow = signal<PitrWindowResponse | null>(null);
  pitrWindowLoading = signal(false);
  selectedPitrTime = signal('');

  locations = signal<Location[]>([]);
  locationsLoading = signal(false);
  restoreNodeRegions = signal<string[]>(['', '', '']);
  restoreClusterName = signal('');

  haMode = signal<boolean>(true);
  selectedCategory = signal<'shared' | 'dedicated'>('shared');
  selectedServerType = signal<string>('cx23');
  serverTypes = signal<ServerTypesResponse | null>(null);
  serverTypesLoading = signal(false);
  serverTypesError = signal<string | null>(null);

  currentServerTypes = computed(() => {
    const types = this.serverTypes();
    if (!types) return [];
    return this.selectedCategory() === 'shared' ? types.shared : types.dedicated;
  });

  filteredLocations = computed(() => {
    const locs = this.locations();
    const types = this.serverTypes();
    const selectedType = this.selectedServerType();

    if (!types) return locs;

    const allTypes = [...types.shared, ...types.dedicated];
    const serverType = allTypes.find(t => t.name === selectedType);

    if (!serverType) return locs;

    return locs.map(loc => ({
      ...loc,
      available: serverType.availableLocations.includes(loc.id)
    }));
  });

  selectedBackupType: 'full' | 'diff' | 'incr' = 'incr';

  private backupToDelete: Backup | null = null;
  private pollingSubscription?: Subscription;
  private maxStorageSize = 0;

  constructor(
    private backupService: BackupService,
    private clusterService: ClusterService,
    private adminService: AdminService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  pitrWindowLabel = computed(() => {
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
    if (!this.isAdmin) {
      this.loadMetrics();
      this.loadPitrWindow();
    }
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }

  private loadBackups(): void {
    const request$ = this.isAdmin
      ? this.adminService.getClusterBackups(this.clusterId)
      : this.backupService.getBackups(this.clusterId);

    request$.subscribe({
      next: (response) => {
        this.backups.set(response.backups);
        this.loading.set(false);
        if (!this.isAdmin) {
          this.loadPitrWindow();
        }
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
        // non-critical
      }
    });
  }

  private loadPitrWindow(): void {
    if (this.isAdmin) return;

    this.pitrWindowLoading.set(true);
    this.backupService.getPitrWindow(this.clusterId).subscribe({
      next: (window) => {
        this.pitrWindow.set(window);
        if (window.available && window.latestPitrTime) {
          this.selectedPitrTime.set(window.latestPitrTime);
        }
        this.pitrWindowLoading.set(false);
      },
      error: () => {
        this.pitrWindowLoading.set(false);
        this.pitrWindow.set({
          available: false,
          earliestPitrTime: null,
          latestPitrTime: null
        });
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
        this.loadMetrics();
        this.loadPitrWindow();
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

    const request$ = this.isAdmin
      ? this.adminService.deleteClusterBackup(this.clusterId, backupId, true)
      : this.backupService.deleteBackup(this.clusterId, backupId, true);

    request$.subscribe({
      next: () => {
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
        this.loadPitrWindow();

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
    this.initializeRestoreForm();
    this.showRestoreDialog.set(true);
  }

  closeRestoreDialog(): void {
    this.showRestoreDialog.set(false);
  }

  openPitrRestoreDialog(): void {
    if (!this.pitrWindow()?.available) return;

    this.initializeRestoreForm();
    this.pitrStep.set(1);

    const defaultTime = this.pitrWindow()?.latestPitrTime;
    if (defaultTime) {
      this.selectedPitrTime.set(defaultTime);
    }

    this.showPitrDialog.set(true);
  }

  closePitrRestoreDialog(): void {
    this.showPitrDialog.set(false);
    this.pitrStep.set(1);
  }

  private initializeRestoreForm(): void {
    this.restoreClusterName.set(this.getRestoredClusterName());

    const sourceNodeCount = this.clusterNodes.length;
    this.haMode.set(sourceNodeCount === 3);

    this.selectedServerType.set(this.sourceNodeSize || 'cx23');
    if (this.sourceNodeSize?.startsWith('ccx')) {
      this.selectedCategory.set('dedicated');
    } else {
      this.selectedCategory.set('shared');
    }

    const sourceRegions = this.clusterNodes.map(n => n.location);
    if (this.haMode()) {
      while (sourceRegions.length < 3) {
        sourceRegions.push(sourceRegions[0] || 'fsn1');
      }
      this.restoreNodeRegions.set(sourceRegions.slice(0, 3));
    } else {
      this.restoreNodeRegions.set([sourceRegions[0] || 'fsn1']);
    }

    this.loadLocations();
    this.loadServerTypes();
  }

  private loadLocations(): void {
    this.locationsLoading.set(true);
    this.clusterService.getLocations().subscribe({
      next: (locs) => {
        this.locations.set(locs);
        this.locationsLoading.set(false);
      },
      error: () => {
        this.locationsLoading.set(false);
        this.notificationService.error('Failed to load locations');
      }
    });
  }

  private loadServerTypes(): void {
    this.serverTypesLoading.set(true);
    this.serverTypesError.set(null);

    this.clusterService.getServerTypes().subscribe({
      next: (types) => {
        this.serverTypes.set(types);
        this.serverTypesLoading.set(false);
      },
      error: (err) => {
        this.serverTypesLoading.set(false);
        this.serverTypesError.set(err.error?.message || 'Failed to load server types');
      }
    });
  }

  toggleHaMode(enabled: boolean): void {
    if (this.haMode() === enabled) return;
    this.haMode.set(enabled);

    if (enabled) {
      const firstRegion = this.restoreNodeRegions()[0] || 'fsn1';
      this.restoreNodeRegions.set([firstRegion, firstRegion, firstRegion]);
    } else {
      const firstRegion = this.restoreNodeRegions()[0] || 'fsn1';
      this.restoreNodeRegions.set([firstRegion]);
    }
  }

  selectCategory(category: 'shared' | 'dedicated'): void {
    if (this.selectedCategory() === category) return;
    this.selectedCategory.set(category);

    const types = this.serverTypes();
    if (types) {
      const categoryTypes = category === 'shared' ? types.shared : types.dedicated;
      const firstAvailable = categoryTypes.find(t => t.availableLocations.length > 0);
      if (firstAvailable) {
        this.selectedServerType.set(firstAvailable.name);
        this.clearInvalidRestoreRegions();
      }
    }
  }

  selectServerType(type: ServerType): void {
    if (!this.isServerTypeAvailable(type)) return;
    this.selectedServerType.set(type.name);
    this.clearInvalidRestoreRegions();
  }

  isServerTypeAvailable(type: ServerType): boolean {
    return type.availableLocations.length > 0;
  }

  getServerTypeClass(type: ServerType): string {
    const isSelected = this.selectedServerType() === type.name;
    const available = this.isServerTypeAvailable(type);

    if (!available) {
      return 'p-3 border rounded text-left transition-all opacity-40 cursor-not-allowed border-border';
    }
    if (isSelected) {
      return 'p-3 border rounded text-left transition-all border-primary bg-primary/10';
    }
    return 'p-3 border rounded text-left transition-all border-border hover:border-muted-foreground';
  }

  private clearInvalidRestoreRegions(): void {
    const regions = [...this.restoreNodeRegions()];
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
      this.restoreNodeRegions.set(regions);
    }
  }

  selectRestoreLocation(nodeIndex: number, locationId: string): void {
    const loc = this.filteredLocations().find(l => l.id === locationId);
    if (!loc?.available) return;

    const regions = [...this.restoreNodeRegions()];
    regions[nodeIndex] = locationId;
    this.restoreNodeRegions.set(regions);
  }

  getRestoreLocationClass(nodeIndex: number, loc: Location): string {
    const base = 'px-1.5 py-2 border text-center transition-all flex flex-col items-center gap-0.5 rounded';
    const isSelected = this.restoreNodeRegions()[nodeIndex] === loc.id;

    if (!loc.available) {
      return `${base} opacity-30 cursor-not-allowed border-border grayscale`;
    }
    if (isSelected) {
      return `${base} border-primary bg-primary/10 text-foreground`;
    }
    return `${base} border-border hover:border-muted-foreground text-muted-foreground`;
  }

  hasUnavailableRestoreSelections(): boolean {
    return this.restoreNodeRegions().some(r => {
      if (!r) return false;
      const loc = this.filteredLocations().find(l => l.id === r);
      return !!loc && !loc.available;
    });
  }

  onRestoreClick(): void {
    if (this.restoring()) return;
    if (!this.validateRestoreConfig()) return;
    this.restoreBackup();
  }

  onPitrTimeSelected(timestamp: string): void {
    this.selectedPitrTime.set(timestamp);
  }

  canContinuePitrStep(): boolean {
    return this.isSelectedPitrTimeInWindow();
  }

  goToPitrStep2(): void {
    if (!this.isSelectedPitrTimeInWindow()) {
      this.notificationService.error('Selected PITR time is outside the available recovery window');
      return;
    }
    this.pitrStep.set(2);
  }

  backToPitrStep1(): void {
    this.pitrStep.set(1);
  }

  onPitrRestoreClick(): void {
    if (this.restoring()) return;

    if (!this.isSelectedPitrTimeInWindow()) {
      this.notificationService.error('Selected PITR time is outside the available recovery window');
      return;
    }

    if (!this.validateRestoreConfig()) return;

    this.restoreFromPitr();
  }

  private isSelectedPitrTimeInWindow(): boolean {
    const window = this.pitrWindow();
    const selected = this.selectedPitrTime();

    if (!window?.available || !selected) return false;

    const targetMs = new Date(selected).getTime();
    if (Number.isNaN(targetMs)) return false;

    if (window.earliestPitrTime) {
      const earliestMs = new Date(window.earliestPitrTime).getTime();
      if (targetMs < earliestMs) return false;
    }

    if (window.latestPitrTime) {
      const latestMs = new Date(window.latestPitrTime).getTime();
      if (targetMs > latestMs) return false;
    }

    return true;
  }

  private validateRestoreConfig(): boolean {
    const name = this.restoreClusterName();
    const regions = this.restoreNodeRegions();
    const expectedNodeCount = this.haMode() ? 3 : 1;

    if (!name || name.length < 3) {
      this.notificationService.error('Cluster name must be at least 3 characters');
      return false;
    }

    if (!/^[a-z][a-z0-9-]*$/.test(name)) {
      this.notificationService.error('Cluster name must start with a letter and contain only lowercase letters, numbers, and hyphens');
      return false;
    }

    if (!this.selectedServerType()) {
      this.notificationService.error('Please select a server type');
      return false;
    }

    if (regions.length !== expectedNodeCount || regions.some(r => !r)) {
      this.notificationService.error(`Please select a region for ${expectedNodeCount === 1 ? 'your node' : 'all ' + expectedNodeCount + ' nodes'}`);
      return false;
    }

    if (this.hasUnavailableRestoreSelections()) {
      this.notificationService.error('One or more selected regions are unavailable. Please select different regions.');
      return false;
    }

    return true;
  }

  restoreBackup(): void {
    const backup = this.selectedBackup();
    if (!backup) return;

    this.restoring.set(true);

    const request = {
      createNewCluster: true,
      newClusterName: this.restoreClusterName(),
      nodeRegions: this.restoreNodeRegions(),
      nodeSize: this.selectedServerType()
    };

    this.backupService.restoreBackup(this.clusterId, backup.id, request).subscribe({
      next: (restoreJob) => {
        this.restoring.set(false);
        this.showRestoreDialog.set(false);
        this.notificationService.success('Restore job started. Navigating to new cluster...');

        if (restoreJob.targetClusterId) {
          this.clusterService.refreshClusters();
          this.router.navigate(['/clusters', restoreJob.targetClusterId]);
        }
      },
      error: (err) => {
        this.restoring.set(false);
        this.notificationService.error(err.error?.message || 'Failed to start restore');
      }
    });
  }

  restoreFromPitr(): void {
    const targetTime = this.selectedPitrTime();
    if (!targetTime) return;

    this.restoring.set(true);

    const request: PitrRestoreRequest = {
      targetTime,
      createNewCluster: true,
      newClusterName: this.restoreClusterName(),
      nodeRegions: this.restoreNodeRegions(),
      nodeSize: this.selectedServerType(),
      postgresVersion: this.sourcePostgresVersion
    };

    this.backupService.restoreFromPitr(this.clusterId, request).subscribe({
      next: (restoreJob) => {
        this.restoring.set(false);
        this.showPitrDialog.set(false);
        this.pitrStep.set(1);
        this.notificationService.success('PITR restore job started. Navigating to new cluster...');

        if (restoreJob.targetClusterId) {
          this.clusterService.refreshClusters();
          this.router.navigate(['/clusters', restoreJob.targetClusterId]);
        }
      },
      error: (err) => {
        this.restoring.set(false);
        this.notificationService.error(err.error?.message || 'Failed to start PITR restore');
      }
    });
  }

  getRestoredClusterName(): string {
    const today = new Date();
    const dateStr = today.toISOString().slice(0, 10).replace(/-/g, '');
    const suffix = `-restored-${dateStr}`;
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

  formatUtcIso(dateString: string | null | undefined): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return '';
    return date.toISOString();
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
      case 'completed': return 'bg-status-running';
      case 'in_progress':
      case 'pending': return 'bg-status-warning animate-pulse';
      case 'failed': return 'bg-status-error';
      default: return 'bg-status-stopped';
    }
  }

  getStatusBadgeClasses(status: BackupStatus): string {
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
