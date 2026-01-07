-- Add cloud storage support columns to backup_metadata table

ALTER TABLE backup_metadata
ADD COLUMN IF NOT EXISTS cloud_storage_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS uploaded_to_cloud BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for cloud upload status
CREATE INDEX IF NOT EXISTS idx_backup_metadata_uploaded_to_cloud ON backup_metadata(uploaded_to_cloud);

-- Add comment
COMMENT ON COLUMN backup_metadata.cloud_storage_url IS 'URL of backup in cloud storage (MinIO/S3)';
COMMENT ON COLUMN backup_metadata.uploaded_to_cloud IS 'Flag indicating if backup has been uploaded to cloud storage';
