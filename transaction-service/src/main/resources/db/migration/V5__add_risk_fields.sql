-- Add risk assessment fields to transactions table
-- These fields store risk-engine results and determine OTP challenge type

ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);

ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS risk_score INTEGER;

ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS challenge_type VARCHAR(20);

-- Add index for querying by challenge type
CREATE INDEX IF NOT EXISTS idx_transactions_challenge_type 
ON transactions(challenge_type);

-- Add index for querying by risk level
CREATE INDEX IF NOT EXISTS idx_transactions_risk_level 
ON transactions(risk_level);

COMMENT ON COLUMN transactions.risk_level IS 'Risk level from risk-engine: LOW, MEDIUM, HIGH';
COMMENT ON COLUMN transactions.risk_score IS 'Risk score from 0-100';
COMMENT ON COLUMN transactions.challenge_type IS 'OTP challenge type: NONE, SMS_OTP, SMART_OTP';
