-- Create vps_nodes table for tracking cluster nodes
CREATE TABLE IF NOT EXISTS vps_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,

    -- Hetzner server info
    hetzner_id BIGINT,
    name VARCHAR(255) NOT NULL,
    public_ip INET,
    private_ip INET,

    -- Node configuration
    server_type VARCHAR(50) NOT NULL DEFAULT 'cx23',
    location VARCHAR(50) NOT NULL DEFAULT 'fsn1',
    status VARCHAR(50) NOT NULL DEFAULT 'creating',
    role VARCHAR(50) NOT NULL DEFAULT 'replica', -- leader, replica

    -- Error tracking
    error_message TEXT,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_vps_nodes_cluster_id ON vps_nodes(cluster_id);
CREATE INDEX IF NOT EXISTS idx_vps_nodes_hetzner_id ON vps_nodes(hetzner_id);
CREATE INDEX IF NOT EXISTS idx_vps_nodes_status ON vps_nodes(status);
