/**
 * Polling interval constants (in milliseconds)
 * Centralized configuration for all polling operations
 */
export const POLLING_INTERVALS = {
  /** Cluster status polling - used on cluster detail page */
  CLUSTER_STATUS: 10_000,

  /** Backup status polling - used when backups are in progress */
  BACKUP_STATUS: 15_000,

  /** Export status polling - used when exports are in progress */
  EXPORT_STATUS: 5_000,
} as const;

export type PollingIntervalKey = keyof typeof POLLING_INTERVALS;
