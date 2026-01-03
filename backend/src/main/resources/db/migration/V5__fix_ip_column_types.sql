-- Fix IP columns from INET to VARCHAR for Hibernate compatibility
ALTER TABLE vps_nodes
    ALTER COLUMN public_ip TYPE VARCHAR(45) USING public_ip::VARCHAR,
    ALTER COLUMN private_ip TYPE VARCHAR(45) USING private_ip::VARCHAR;
