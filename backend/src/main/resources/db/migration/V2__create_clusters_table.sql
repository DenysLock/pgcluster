-- Create clusters table
CREATE TABLE IF NOT EXISTS clusters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    plan VARCHAR(50) NOT NULL DEFAULT 'dedicated',
    status VARCHAR(50) NOT NULL DEFAULT 'pending',

    -- Cluster configuration
    postgres_version VARCHAR(10) NOT NULL DEFAULT '16',
    node_count INT NOT NULL DEFAULT 3,
    node_size VARCHAR(50) NOT NULL DEFAULT 'cx23',
    region VARCHAR(50) NOT NULL DEFAULT 'fsn1',

    -- Connection info
    hostname VARCHAR(255),
    port INT DEFAULT 5432,
    postgres_password VARCHAR(255),

    -- Resource tracking
    storage_gb INT DEFAULT 40,
    memory_mb INT DEFAULT 4096,
    cpu_cores INT DEFAULT 2,

    -- Error tracking
    error_message TEXT,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_clusters_user_id ON clusters(user_id);
CREATE INDEX IF NOT EXISTS idx_clusters_slug ON clusters(slug);
CREATE INDEX IF NOT EXISTS idx_clusters_status ON clusters(status);
