-- V6: Add challenge_id for Smart OTP challenge-response flow
-- Vietnamese e-banking style verification

ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS challenge_id VARCHAR(100);

-- Index for efficient challenge lookups
CREATE INDEX IF NOT EXISTS idx_transactions_challenge_id ON transactions(challenge_id);

-- Comment on new column
COMMENT ON COLUMN transactions.challenge_id IS 'Challenge ID from user-service Smart OTP for challenge-response verification';
