export type BackupStatus = 'pending' | 'in_progress' | 'completed' | 'failed' | 'expired' | 'deleted';
export type BackupType = 'manual' | 'scheduled_daily' | 'scheduled_weekly' | 'scheduled_monthly';
export type BackupPhysicalType = 'full' | 'diff' | 'incr';
export type BackupStep = 'pending' | 'preparing' | 'backing_up' | 'uploading' | 'verifying' | 'completed' | 'failed';
export type RestoreType = 'full' | 'pitr';
export type RestoreJobStatus = 'pending' | 'in_progress' | 'completed' | 'failed' | 'cancelled';
export type ExportStatus = 'pending' | 'in_progress' | 'completed' | 'failed';

export interface Backup {
  id: string;
  clusterId: string;
  type: BackupType;
  backupType: BackupPhysicalType | null;
  status: BackupStatus;
  currentStep: BackupStep | null;
  progressPercent: number | null;
  sizeBytes: number | null;
  formattedSize: string | null;
  s3BasePath: string | null;
  walStartLsn: string | null;
  walEndLsn: string | null;
  earliestRecoveryTime: string | null;
  latestRecoveryTime: string | null;
  retentionType: string | null;
  expiresAt: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  pgbackrestLabel: string | null;
}

export interface BackupListResponse {
  backups: Backup[];
  count: number;
  totalSizeBytes: number;
  formattedTotalSize: string;
}

export interface BackupMetrics {
  totalSizeBytes: number;
  formattedTotalSize: string;
  backupCount: number;
  oldestBackup: string | null;
  newestBackup: string | null;
  earliestPitrTime: string | null;
  latestPitrTime: string | null;
  storageTrend: StorageTrendPoint[];
}

export interface StorageTrendPoint {
  date: string;
  sizeBytes: number;
  formattedSize: string;
}

export interface RestoreRequest {
  targetTime?: string;
  createNewCluster?: boolean;
  newClusterName?: string;
  nodeRegions?: string[];
  nodeSize?: string;
  postgresVersion?: string;
}

export interface PitrRestoreRequest {
  targetTime: string;
  createNewCluster?: boolean;
  newClusterName?: string;
  nodeRegions?: string[];
  nodeSize?: string;
  postgresVersion?: string;
}

export interface PitrWindowResponse {
  available: boolean;
  earliestPitrTime: string | null;
  latestPitrTime: string | null;
  intervals: PitrWindowInterval[];
  status: PitrWindowStatus | string | null;
  unavailableReason: string | null;
  asOf: string | null;
}

export type PitrWindowStatus = 'continuous' | 'segmented' | 'unavailable';

export interface PitrWindowInterval {
  startTime: string;
  endTime: string;
}

export interface PitrValidationErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message: string;
  code: string;
  requestedTargetTime: string;
  nearestBefore: string | null;
  nearestAfter: string | null;
  earliestPitrTime: string | null;
  latestPitrTime: string | null;
}

export interface BackupDeletionInfo {
  backup: Backup;
  dependentBackups: Backup[];
  totalCount: number;
  totalSizeBytes: number;
  formattedTotalSize: string;
  requiresConfirmation: boolean;
  warningMessage: string | null;
}

export interface RestoreJob {
  id: string;
  sourceClusterId: string;
  targetClusterId: string | null;
  backupId: string;
  restoreType: RestoreType;
  targetTime: string | null;
  status: RestoreJobStatus;
  currentStep: string | null;
  progress: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface Export {
  id: string;
  clusterId: string;
  status: ExportStatus;
  format: string;
  sizeBytes: number | null;
  formattedSize: string | null;
  downloadUrl: string | null;
  downloadExpiresAt: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
