-- =======================================================
-- Transaction Service Database Schema
-- Saga Orchestrator Pattern with Outbox
-- =======================================================

-- Main Transactions Table (Saga State Machine)
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_account_id VARCHAR(255) NOT NULL,
    receiver_account_id VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL,
    fee_amount NUMERIC(19, 2) DEFAULT 0.00,
    description TEXT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    -- Transfer Type and External Transfer Fields
    transfer_type VARCHAR(20),
    external_transaction_id VARCHAR(255),
    destination_bank_code VARCHAR(50),
    
    -- Saga Orchestration Fields
    correlation_id VARCHAR(255) UNIQUE,
    current_step VARCHAR(50), -- STARTED, DEBITED, CREDITED, COMPLETED
    failure_step VARCHAR(50), -- Step where failure occurred
    failure_reason TEXT,
    
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT transactions_status_check CHECK (
        status IN ('PENDING_OTP', 'PENDING', 'PROCESSING', 'COMPLETED', 'SUCCESS', 'FAILED', 'CANCELLED', 'OTP_EXPIRED', 'ROLLBACK_FAILED')
    ),
    CONSTRAINT transactions_transaction_type_check CHECK (
        transaction_type IN ('INTERNAL_TRANSFER', 'EXTERNAL_TRANSFER', 'BILL_PAYMENT', 'DEPOSIT', 'WITHDRAWAL')
    ),
    CONSTRAINT transactions_transfer_type_check CHECK (
        transfer_type IS NULL OR transfer_type IN ('INTERNAL', 'EXTERNAL')
    ),
    CONSTRAINT transactions_current_step_check CHECK (
        current_step IS NULL OR current_step IN ('STARTED', 'OTP_VERIFIED', 'DEBITED', 'CREDITED', 'COMPLETED', 'ROLLING_BACK', 'ROLLED_BACK', 'FAILED', 'ROLLBACK_FAILED' ,'EXTERNAL_INITIATED')
    )
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_sender_date ON transactions(sender_account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_receiver_date ON transactions(receiver_account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_status_date ON transactions(status, created_at);
CREATE INDEX IF NOT EXISTS idx_correlation_id ON transactions(correlation_id);
CREATE INDEX IF NOT EXISTS idx_current_step ON transactions(current_step);

-- Transaction Limits Table
CREATE TABLE IF NOT EXISTS transaction_limits (
    account_id VARCHAR(255) PRIMARY KEY,
    daily_limit NUMERIC(19, 2) NOT NULL DEFAULT 50000000.00,
    daily_used NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    last_daily_reset TIMESTAMP,
    monthly_limit NUMERIC(19, 2) NOT NULL DEFAULT 500000000.00,
    monthly_used NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    last_monthly_reset TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Fee Configuration Table
CREATE TABLE IF NOT EXISTS transaction_fees (
    fee_id BIGSERIAL PRIMARY KEY,
    transaction_type VARCHAR(20) UNIQUE NOT NULL,
    fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT transaction_fees_transaction_type_check CHECK (
        transaction_type IN ('INTERNAL_TRANSFER', 'EXTERNAL_TRANSFER', 'BILL_PAYMENT', 'DEPOSIT', 'WITHDRAWAL')
    )
);

-- Transaction OTPs Table
-- Stores OTP codes for transaction verification
CREATE TABLE IF NOT EXISTS transaction_otps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT false,
    verified_at TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_transaction_otp FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id) ON DELETE CASCADE
);

-- Index for OTP lookup
CREATE INDEX IF NOT EXISTS idx_otp_transaction ON transaction_otps(transaction_id);
CREATE INDEX IF NOT EXISTS idx_otp_expires ON transaction_otps(expires_at);

-- Outbox Events Table (Transactional Outbox Pattern)
-- Ensures exactly-once delivery to RabbitMQ
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL, -- Transaction, User, Account
    aggregate_id VARCHAR(100) NOT NULL, -- transaction_id or user_id
    event_type VARCHAR(100) NOT NULL, -- TransactionCreated, OTPGenerated, DebitAccount, CreditAccount
    exchange VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL, -- JSON payload as text
    
    -- Event Processing Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    error_message TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    
    CONSTRAINT outbox_events_status_check CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    )
);

-- Indexes for outbox processing
CREATE INDEX IF NOT EXISTS idx_status_created ON outbox_events(status, created_at);
CREATE INDEX IF NOT EXISTS idx_aggregate ON outbox_events(aggregate_type, aggregate_id);
