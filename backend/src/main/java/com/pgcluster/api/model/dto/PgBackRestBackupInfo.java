package com.pgcluster.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing backup information returned by pgBackRest info command.
 * Used internally by PgBackRestService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgBackRestBackupInfo {

    /**
     * pgBackRest backup label (e.g., "20240115-120000F")
     */
    private String label;

    /**
     * Backup type: full, diff, or incr
     */
    private String type;

    /**
     * Database size at time of backup (in bytes)
     */
    private Long databaseSizeBytes;

    /**
     * Backup size on disk (in bytes, after compression)
     */
    private Long backupSizeBytes;

    /**
     * WAL start position
     */
    private String walStartLsn;

    /**
     * WAL stop position
     */
    private String walStopLsn;

    /**
     * Backup start timestamp
     */
    private Instant startTime;

    /**
     * Backup stop timestamp
     */
    private Instant stopTime;

    /**
     * Prior backup label this backup depends on (for diff/incr)
     */
    private String priorLabel;

    /**
     * Reference backups this backup depends on
     */
    private String[] referenceBackups;

    /**
     * pgBackRest backup type constants
     */
    public static final String TYPE_FULL = "full";
    public static final String TYPE_DIFF = "diff";
    public static final String TYPE_INCR = "incr";
}
