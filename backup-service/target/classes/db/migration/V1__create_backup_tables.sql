-- Create backup_metadata table
CREATE TABLE IF NOT EXISTS backup_metadata (
    backup_id UUID PRIMARY KEY,
    backup_name VARCHAR(255) NOT NULL,
    backup_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    total_size_bytes BIGINT,
    backup_path VARCHAR(500),
    compressed BOOLEAN NOT NULL DEFAULT TRUE,
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    checksum VARCHAR(255),
    error_message VARCHAR(1000),
    initiated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create service_backup_info table
CREATE TABLE IF NOT EXISTS service_backup_info (
    id UUID PRIMARY KEY,
    backup_id UUID NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    container_name VARCHAR(100) NOT NULL,
    file_path VARCHAR(500),
    file_size_bytes BIGINT,
    record_count BIGINT,
    checksum VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    error_message VARCHAR(500),
    backup_duration_ms BIGINT,
    CONSTRAINT fk_backup_metadata
        FOREIGN KEY (backup_id)
        REFERENCES backup_metadata(backup_id)
        ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_backup_metadata_status ON backup_metadata(status);
CREATE INDEX IF NOT EXISTS idx_backup_metadata_created_at ON backup_metadata(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_backup_metadata_type ON backup_metadata(backup_type);
CREATE INDEX IF NOT EXISTS idx_service_backup_info_backup_id ON service_backup_info(backup_id);
CREATE INDEX IF NOT EXISTS idx_service_backup_info_service_name ON service_backup_info(service_name);

-- Add comments for documentation
COMMENT ON TABLE backup_metadata IS 'Stores metadata for all backup operations';
COMMENT ON TABLE service_backup_info IS 'Stores individual service backup information for each backup operation';

COMMENT ON COLUMN backup_metadata.backup_type IS 'FULL, INCREMENTAL, SINGLE_SERVICE, or CUSTOM';
COMMENT ON COLUMN backup_metadata.status IS 'PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED, VALIDATING, RESTORING';

-- Create outbox_events table for event outbox pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    exchange VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_status_created ON outbox_events(status, created_at);
CREATE INDEX IF NOT EXISTS idx_aggregate ON outbox_events(aggregate_type, aggregate_id);
