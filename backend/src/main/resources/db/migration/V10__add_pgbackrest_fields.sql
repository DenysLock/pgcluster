-- Add pgBackRest-specific fields to backups table
ALTER TABLE backups ADD COLUMN IF NOT EXISTS pgbackrest_label VARCHAR(50);
ALTER TABLE backups ADD COLUMN IF NOT EXISTS backup_type VARCHAR(20);

-- backup_type: full, diff, incr (pgBackRest backup types)
-- type: manual, scheduled_daily, etc. (trigger type - already exists)

-- Add index for backup_type for filtering
CREATE INDEX IF NOT EXISTS idx_backups_backup_type ON backups(backup_type);

-- Add comment for clarity
COMMENT ON COLUMN backups.pgbackrest_label IS 'pgBackRest backup label (e.g., 20240115-120000F)';
COMMENT ON COLUMN backups.backup_type IS 'pgBackRest backup type: full, diff, incr';
