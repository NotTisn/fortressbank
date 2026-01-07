-- =====================================================
-- V7__Insert_additional_test_accounts.sql
-- Insert thêm 20 sample accounts để phục vụ testing
-- và migration data của transaction-service
-- =====================================================

-- Insert 20 test accounts với account_id và account_number chuẩn
INSERT INTO accounts (account_id, account_number, user_id, balance, pin_hash, status, created_at) VALUES
-- Accounts 1-5
('acc-001', '1234567890', 'user-001', 50000000.00, NULL, 'ACTIVE', NOW()),
('acc-002', '2345678901', 'user-002', 30000000.00, NULL, 'ACTIVE', NOW()),
('acc-003', '3456789012', 'user-003', 45000000.00, NULL, 'ACTIVE', NOW()),
('acc-004', '4567890123', 'user-004', 25000000.00, NULL, 'ACTIVE', NOW()),
('acc-005', '5678901234', 'user-005', 60000000.00, NULL, 'ACTIVE', NOW()),

-- Accounts 6-10
('acc-006', '6789012345', 'user-006', 35000000.00, NULL, 'ACTIVE', NOW()),
('acc-007', '7890123456', 'user-007', 40000000.00, NULL, 'ACTIVE', NOW()),
('acc-008', '8901234567', 'user-008', 55000000.00, NULL, 'ACTIVE', NOW()),
('acc-009', '9012345678', 'user-009', 20000000.00, NULL, 'ACTIVE', NOW()),
('acc-010', '0123456789', 'user-010', 70000000.00, NULL, 'ACTIVE', NOW()),

-- Accounts 11-15
('acc-011', '1122334455', 'user-011', 38000000.00, NULL, 'ACTIVE', NOW()),
('acc-012', '2233445566', 'user-012', 42000000.00, NULL, 'ACTIVE', NOW()),
('acc-013', '3344556677', 'user-013', 32000000.00, NULL, 'ACTIVE', NOW()),
('acc-014', '4455667788', 'user-014', 48000000.00, NULL, 'ACTIVE', NOW()),
('acc-015', '5566778899', 'user-015', 52000000.00, NULL, 'ACTIVE', NOW()),

-- Accounts 16-20
('acc-016', '6677889900', 'user-016', 28000000.00, NULL, 'ACTIVE', NOW()),
('acc-017', '7788990011', 'user-017', 65000000.00, NULL, 'ACTIVE', NOW()),
('acc-018', '8899001122', 'user-018', 33000000.00, NULL, 'ACTIVE', NOW()),
('acc-019', '9900112233', 'user-019', 47000000.00, NULL, 'ACTIVE', NOW()),
('acc-020', '0011223344', 'user-020', 58000000.00, NULL, 'ACTIVE', NOW())

ON CONFLICT (account_id) DO NOTHING;

-- Note: ON CONFLICT DO NOTHING để tránh lỗi nếu chạy lại migration
-- hoặc nếu accounts đã tồn tại
