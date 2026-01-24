-- SSH Host Keys table for Trust-On-First-Use (TOFU) verification
-- Stores SSH host key fingerprints to persist across application restarts

CREATE TABLE ssh_host_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host VARCHAR(255) NOT NULL UNIQUE,
    fingerprint VARCHAR(255) NOT NULL,
    key_type VARCHAR(50),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_verified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for fast lookups by host
CREATE INDEX idx_ssh_host_keys_host ON ssh_host_keys(host);

-- Trigger to update updated_at
CREATE TRIGGER update_ssh_host_keys_updated_at
    BEFORE UPDATE ON ssh_host_keys
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE ssh_host_keys IS 'Stores SSH host key fingerprints for TOFU verification';
COMMENT ON COLUMN ssh_host_keys.host IS 'Hostname or IP address';
COMMENT ON COLUMN ssh_host_keys.fingerprint IS 'SHA-256 fingerprint of the public key (base64 encoded)';
COMMENT ON COLUMN ssh_host_keys.key_type IS 'SSH key type (e.g., ssh-rsa, ssh-ed25519)';
COMMENT ON COLUMN ssh_host_keys.first_seen_at IS 'When this host key was first trusted';
COMMENT ON COLUMN ssh_host_keys.last_verified_at IS 'When this host key was last successfully verified';
