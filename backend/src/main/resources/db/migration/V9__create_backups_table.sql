-- Backups table for storing backup metadata
CREATE TABLE backups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    size_bytes BIGINT,
    s3_base_path VARCHAR(500),
    s3_wal_path VARCHAR(500),
    wal_start_lsn VARCHAR(50),
    wal_end_lsn VARCHAR(50),
    earliest_recovery_time TIMESTAMP WITH TIME ZONE,
    latest_recovery_time TIMESTAMP WITH TIME ZONE,
    retention_type VARCHAR(50),
    expires_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes for common queries
CREATE INDEX idx_backups_cluster_id ON backups(cluster_id);
CREATE INDEX idx_backups_status ON backups(status);
CREATE INDEX idx_backups_type ON backups(type);
CREATE INDEX idx_backups_expires_at ON backups(expires_at);
CREATE INDEX idx_backups_created_at ON backups(created_at DESC);

-- Restore jobs table for tracking restore operations
CREATE TABLE restore_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_cluster_id UUID NOT NULL REFERENCES clusters(id),
    target_cluster_id UUID REFERENCES clusters(id),
    backup_id UUID NOT NULL REFERENCES backups(id),
    restore_type VARCHAR(50) NOT NULL,
    target_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    current_step VARCHAR(100),
    progress INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for restore jobs
CREATE INDEX idx_restore_jobs_source_cluster_id ON restore_jobs(source_cluster_id);
CREATE INDEX idx_restore_jobs_target_cluster_id ON restore_jobs(target_cluster_id);
CREATE INDEX idx_restore_jobs_backup_id ON restore_jobs(backup_id);
CREATE INDEX idx_restore_jobs_status ON restore_jobs(status);

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_backups_updated_at
    BEFORE UPDATE ON backups
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_restore_jobs_updated_at
    BEFORE UPDATE ON restore_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
