-- Add progress tracking fields to backups table
ALTER TABLE backups ADD COLUMN IF NOT EXISTS current_step VARCHAR(50);
ALTER TABLE backups ADD COLUMN IF NOT EXISTS progress_percent INTEGER DEFAULT 0;

-- Add comments for clarity
COMMENT ON COLUMN backups.current_step IS 'Current backup step: pending, preparing, backing_up, uploading, verifying, completed';
COMMENT ON COLUMN backups.progress_percent IS 'Backup progress percentage (0-100)';

-- Create index for filtering by step
CREATE INDEX IF NOT EXISTS idx_backups_current_step ON backups(current_step);
