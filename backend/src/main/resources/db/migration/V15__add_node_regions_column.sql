-- Add node_regions column to store per-node region assignments as JSON array
ALTER TABLE clusters ADD COLUMN node_regions TEXT;