-- V6: Add devices table for device-bound Smart OTP
-- Each registered device stores a public key for challenge-response verification
-- This enables Vietnamese e-banking style biometric verification (fingerprint/PIN gated)

CREATE TABLE devices (
    device_id VARCHAR(200) PRIMARY KEY,
    user_id VARCHAR(200) NOT NULL,
    name VARCHAR(255),
    public_key_pem TEXT,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Index for finding user's devices
CREATE INDEX idx_devices_user_id ON devices(user_id);

-- Index for active devices lookup
CREATE INDEX idx_devices_user_active ON devices(user_id, is_active);

COMMENT ON TABLE devices IS 'Registered devices for device-bound Smart OTP verification';
COMMENT ON COLUMN devices.public_key_pem IS 'Device public key (RSA/EC) for challenge signature verification';
COMMENT ON COLUMN devices.is_active IS 'FALSE when device is revoked by user';
