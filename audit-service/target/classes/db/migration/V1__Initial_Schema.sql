-- Create account audit logs table
CREATE TABLE IF NOT EXISTS account_audit_logs (
    id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    old_values TEXT,
    new_values TEXT,
    changes TEXT,
    result VARCHAR(50),
    error_message TEXT,
    metadata TEXT,
    timestamp TIMESTAMP NOT NULL,
    CONSTRAINT check_result CHECK (result IN ('SUCCESS', 'FAILURE', 'PENDING'))
);

-- Create indexes for account audit logs
CREATE INDEX IF NOT EXISTS idx_account_service_name ON account_audit_logs(service_name);
CREATE INDEX IF NOT EXISTS idx_account_entity_type ON account_audit_logs(entity_type);
CREATE INDEX IF NOT EXISTS idx_account_entity_id ON account_audit_logs(entity_id);
CREATE INDEX IF NOT EXISTS idx_account_action ON account_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_account_user_id ON account_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_account_timestamp ON account_audit_logs(timestamp);

-- Create transaction audit logs table
CREATE TABLE IF NOT EXISTS transaction_audit_logs (
    id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    old_values TEXT,
    new_values TEXT,
    changes TEXT,
    result VARCHAR(50),
    error_message TEXT,
    metadata TEXT,
    timestamp TIMESTAMP NOT NULL,
    CONSTRAINT check_transaction_result CHECK (result IN ('SUCCESS', 'FAILURE', 'PENDING'))
);

-- Create indexes for transaction audit logs
CREATE INDEX IF NOT EXISTS idx_transaction_service_name ON transaction_audit_logs(service_name);
CREATE INDEX IF NOT EXISTS idx_transaction_entity_type ON transaction_audit_logs(entity_type);
CREATE INDEX IF NOT EXISTS idx_transaction_entity_id ON transaction_audit_logs(entity_id);
CREATE INDEX IF NOT EXISTS idx_transaction_action ON transaction_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_transaction_user_id ON transaction_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_transaction_timestamp ON transaction_audit_logs(timestamp);

-- Create user audit logs table
CREATE TABLE IF NOT EXISTS user_audit_logs (
    id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    old_values TEXT,
    new_values TEXT,
    changes TEXT,
    result VARCHAR(50),
    error_message TEXT,
    metadata TEXT,
    timestamp TIMESTAMP NOT NULL,
    CONSTRAINT check_user_result CHECK (result IN ('SUCCESS', 'FAILURE', 'PENDING'))
);

-- Create indexes for user audit logs
CREATE INDEX IF NOT EXISTS idx_user_service_name ON user_audit_logs(service_name);
CREATE INDEX IF NOT EXISTS idx_user_entity_type ON user_audit_logs(entity_type);
CREATE INDEX IF NOT EXISTS idx_user_entity_id ON user_audit_logs(entity_id);
CREATE INDEX IF NOT EXISTS idx_user_action ON user_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_user_user_id ON user_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_user_timestamp ON user_audit_logs(timestamp);
