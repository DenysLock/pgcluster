-- Add provisioning progress tracking columns to clusters table
ALTER TABLE clusters ADD COLUMN provisioning_step VARCHAR(50);
ALTER TABLE clusters ADD COLUMN provisioning_progress INTEGER;
