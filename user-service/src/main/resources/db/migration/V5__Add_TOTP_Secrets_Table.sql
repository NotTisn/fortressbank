-- V5: Add TOTP (Smart OTP) secrets table
-- Stores encrypted TOTP secrets for Google Authenticator compatible Smart OTP

CREATE TABLE otp_secrets (
    user_id VARCHAR(255) PRIMARY KEY,
    otp_secret_key_encrypt VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP,
    failed_attempts INTEGER DEFAULT 0,
    last_failed_at TIMESTAMP,
    recovery_codes VARCHAR(1000),
    
    CONSTRAINT chk_otp_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED'))
);

-- Index for quick status lookups
CREATE INDEX idx_otp_secrets_status ON otp_secrets(status);

-- Add comment for documentation
COMMENT ON TABLE otp_secrets IS 'TOTP secrets for Smart OTP (Google Authenticator compatible)';
COMMENT ON COLUMN otp_secrets.otp_secret_key_encrypt IS 'AES-256-GCM encrypted TOTP secret key';
COMMENT ON COLUMN otp_secrets.recovery_codes IS 'Comma-separated SHA-256 hashed recovery codes';
