-- Exports table for pg_dump database exports
CREATE TABLE exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    format VARCHAR(50) NOT NULL DEFAULT 'pg_dump',
    size_bytes BIGINT,
    s3_path VARCHAR(500),
    download_url VARCHAR(2000),
    download_expires_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes for common queries
CREATE INDEX idx_exports_cluster_id ON exports(cluster_id);
CREATE INDEX idx_exports_status ON exports(status);
CREATE INDEX idx_exports_created_at ON exports(created_at DESC);

-- Add trigger for updated_at
CREATE TRIGGER update_exports_updated_at
    BEFORE UPDATE ON exports
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add comments
COMMENT ON TABLE exports IS 'Database exports (pg_dump) with S3 storage and time-limited download links';
COMMENT ON COLUMN exports.format IS 'Export format: pg_dump (SQL), custom, directory';
COMMENT ON COLUMN exports.download_url IS 'Presigned S3 download URL (time-limited)';
