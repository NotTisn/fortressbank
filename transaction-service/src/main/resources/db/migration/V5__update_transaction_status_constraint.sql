-- Add PENDING_FACE_AUTH to transactions status check constraint
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;

ALTER TABLE transactions ADD CONSTRAINT transactions_status_check 
CHECK (status IN ('PENDING_OTP', 'PENDING_FACE_AUTH', 'PENDING', 'PROCESSING', 'COMPLETED', 'SUCCESS', 'FAILED', 'CANCELLED', 'OTP_EXPIRED', 'ROLLBACK_FAILED', 'ROLLBACK_COMPLETED'));
