-- Add requested_backup_type column to track what user requested (full/diff/incr)
-- The existing backup_type column stores the actual type after pgBackRest runs
ALTER TABLE backups ADD COLUMN requested_backup_type VARCHAR(20);
