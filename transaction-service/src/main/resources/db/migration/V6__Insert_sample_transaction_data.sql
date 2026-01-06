-- =====================================================
-- V6__Insert_sample_transaction_data.sql
-- Sample transaction data for admin dashboard testing
-- Tạo nhiều transaction với nhiều loại khác nhau
-- =====================================================

-- NOTE: Đảm bảo rằng account_id và account_number phải tồn tại trong account-service
-- Nếu không có data trong account-service, phải insert vào đó trước

-- Insert transactions for TODAY (sử dụng CURRENT_DATE)
-- Internal Transfers (Chuyển khoản nội bộ)
INSERT INTO transactions (
    transaction_id,
    sender_account_id,
    sender_account_number,
    sender_user_id,
    receiver_account_id,
    receiver_account_number,
    receiver_user_id,
    amount,
    fee_amount,
    description,
    transaction_type,
    status,
    transfer_type,
    correlation_id,
    current_step,
    created_at,
    updated_at,
    completed_at
) VALUES
-- TODAY - SUCCESSFUL INTERNAL TRANSFERS
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', 'acc-002', '2345678901', 'user-002', 500000.00, 0.00, 'Chuyển tiền mua hàng', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', 'acc-004', '4567890123', 'user-004', 1200000.00, 0.00, 'Trả nợ bạn bè', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', 'acc-006', '6789012345', 'user-006', 800000.00, 0.00, 'Tiền thuê nhà', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(gen_random_uuid(), 'acc-007', '7890123456', 'user-007', 'acc-008', '8901234567', 'user-008', 2500000.00, 0.00, 'Thanh toán hợp đồng', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours'),
(gen_random_uuid(), 'acc-009', '9012345678', 'user-009', 'acc-010', '0123456789', 'user-010', 350000.00, 0.00, 'Chia tiền ăn', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours'),
(gen_random_uuid(), 'acc-011', '1122334455', 'user-011', 'acc-012', '2233445566', 'user-012', 950000.00, 0.00, 'Chuyển tiền học phí', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP - INTERVAL '5 hours'),
(gen_random_uuid(), 'acc-013', '3344556677', 'user-013', 'acc-014', '4455667788', 'user-014', 1800000.00, 0.00, 'Tiền mua sắm', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '6 hours', CURRENT_TIMESTAMP - INTERVAL '6 hours', CURRENT_TIMESTAMP - INTERVAL '6 hours'),
(gen_random_uuid(), 'acc-015', '5566778899', 'user-015', 'acc-016', '6677889900', 'user-016', 650000.00, 0.00, 'Chi phí du lịch', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '7 hours', CURRENT_TIMESTAMP - INTERVAL '7 hours', CURRENT_TIMESTAMP - INTERVAL '7 hours'),
(gen_random_uuid(), 'acc-017', '7788990011', 'user-017', 'acc-018', '8899001122', 'user-018', 420000.00, 0.00, 'Đóng góp từ thiện', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '8 hours', CURRENT_TIMESTAMP - INTERVAL '8 hours', CURRENT_TIMESTAMP - INTERVAL '8 hours'),
(gen_random_uuid(), 'acc-019', '9900112233', 'user-019', 'acc-020', '0011223344', 'user-020', 3200000.00, 0.00, 'Thanh toán dịch vụ', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '9 hours', CURRENT_TIMESTAMP - INTERVAL '9 hours', CURRENT_TIMESTAMP - INTERVAL '9 hours'),

-- TODAY - EXTERNAL TRANSFERS
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', NULL, '9876543210', NULL, 2000000.00, 5000.00, 'Chuyển tiền liên ngân hàng', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour'),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', NULL, '8765432109', NULL, 1500000.00, 5000.00, 'Thanh toán nhà cung cấp', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', NULL, '7654321098', NULL, 3500000.00, 5000.00, 'Chuyển tiền kinh doanh', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours'),
(gen_random_uuid(), 'acc-007', '7890123456', 'user-007', NULL, '6543210987', NULL, 850000.00, 5000.00, 'Thanh toán hóa đơn', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours'),
(gen_random_uuid(), 'acc-009', '9012345678', 'user-009', NULL, '5432109876', NULL, 1200000.00, 5000.00, 'Chuyển tiền gia đình', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP - INTERVAL '5 hours'),

-- TODAY - DEPOSITS
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-002', '2345678901', 'user-002', 5000000.00, 0.00, 'Admin nạp tiền vào tài khoản', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-004', '4567890123', 'user-004', 3000000.00, 0.00, 'Nạp tiền từ Stripe', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-006', '6789012345', 'user-006', 10000000.00, 0.00, 'Nạp tiền lương', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-008', '8901234567', 'user-008', 2500000.00, 0.00, 'Hoàn tiền giao dịch', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-010', '0123456789', 'user-010', 7500000.00, 0.00, 'Nạp tiền thưởng', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours'),

-- TODAY - WITHDRAWALS
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', NULL, 'ATM-001', NULL, 1000000.00, 3000.00, 'Rút tiền tại ATM', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '45 minutes', CURRENT_TIMESTAMP - INTERVAL '45 minutes', CURRENT_TIMESTAMP - INTERVAL '45 minutes'),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', NULL, 'ATM-002', NULL, 500000.00, 3000.00, 'Rút tiền ATM', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', NULL, 'ATM-003', NULL, 2000000.00, 3000.00, 'Rút tiền mặt', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours'),

-- TODAY - BILL PAYMENTS
(gen_random_uuid(), 'acc-002', '2345678901', 'user-002', NULL, 'EVN-12345', NULL, 450000.00, 2000.00, 'Thanh toán tiền điện', 'BILL_PAYMENT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour'),
(gen_random_uuid(), 'acc-004', '4567890123', 'user-004', NULL, 'VNPT-67890', NULL, 250000.00, 2000.00, 'Thanh toán tiền internet', 'BILL_PAYMENT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours'),

-- TODAY - FAILED TRANSACTIONS
(gen_random_uuid(), 'acc-007', '7890123456', 'user-007', 'acc-008', '8901234567', 'user-008', 5000000.00, 0.00, 'Chuyển tiền thất bại', 'INTERNAL_TRANSFER', 'FAILED', 'INTERNAL', 'txn-' || gen_random_uuid(), 'FAILED', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '30 minutes', NULL),
(gen_random_uuid(), 'acc-009', '9012345678', 'user-009', NULL, '1111111111', NULL, 15000000.00, 5000.00, 'Chuyển khoản vượt hạn mức', 'EXTERNAL_TRANSFER', 'FAILED', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'FAILED', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL),

-- TODAY - PENDING TRANSACTIONS
(gen_random_uuid(), 'acc-011', '1122334455', 'user-011', 'acc-012', '2233445566', 'user-012', 750000.00, 0.00, 'Đang chờ xử lý', 'INTERNAL_TRANSFER', 'PENDING', 'INTERNAL', 'txn-' || gen_random_uuid(), 'DEBITED', CURRENT_TIMESTAMP - INTERVAL '15 minutes', CURRENT_TIMESTAMP - INTERVAL '15 minutes', NULL),
(gen_random_uuid(), 'acc-013', '3344556677', 'user-013', NULL, '2222222222', NULL, 1800000.00, 5000.00, 'Đang xử lý chuyển khoản', 'EXTERNAL_TRANSFER', 'PROCESSING', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'EXTERNAL_INITIATED', CURRENT_TIMESTAMP - INTERVAL '20 minutes', CURRENT_TIMESTAMP - INTERVAL '20 minutes', NULL);


-- ==========================================
-- YESTERDAY AND THIS WEEK DATA
-- ==========================================

INSERT INTO transactions (
    transaction_id,
    sender_account_id,
    sender_account_number,
    sender_user_id,
    receiver_account_id,
    receiver_account_number,
    receiver_user_id,
    amount,
    fee_amount,
    description,
    transaction_type,
    status,
    transfer_type,
    correlation_id,
    current_step,
    created_at,
    updated_at,
    completed_at
) VALUES
-- YESTERDAY
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', 'acc-002', '2345678901', 'user-002', 850000.00, 0.00, 'Chuyển tiền hôm qua', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '10 hours'),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', 'acc-004', '4567890123', 'user-004', 1250000.00, 0.00, 'Thanh toán hôm qua', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '14 hours'),
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', NULL, '3333333333', NULL, 2800000.00, 5000.00, 'Chuyển khoản liên NH hôm qua', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '16 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-006', '6789012345', 'user-006', 4500000.00, 0.00, 'Nạp tiền hôm qua', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '9 hours'),
(gen_random_uuid(), 'acc-007', '7890123456', 'user-007', NULL, 'ATM-004', NULL, 800000.00, 3000.00, 'Rút tiền hôm qua', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '1 day' + INTERVAL '11 hours'),

-- 2 DAYS AGO
(gen_random_uuid(), 'acc-009', '9012345678', 'user-009', 'acc-010', '0123456789', 'user-010', 950000.00, 0.00, 'Chuyển tiền 2 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '15 hours'),
(gen_random_uuid(), 'acc-011', '1122334455', 'user-011', NULL, '4444444444', NULL, 3200000.00, 5000.00, 'External transfer 2 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '13 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-012', '2233445566', 'user-012', 6000000.00, 0.00, 'Deposit 2 ngày trước', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '2 days' + INTERVAL '10 hours'),

-- 3 DAYS AGO
(gen_random_uuid(), 'acc-013', '3344556677', 'user-013', 'acc-014', '4455667788', 'user-014', 1150000.00, 0.00, 'Chuyển tiền 3 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '12 hours', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '12 hours', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '12 hours'),
(gen_random_uuid(), 'acc-015', '5566778899', 'user-015', 'acc-016', '6677889900', 'user-016', 2300000.00, 0.00, 'Transfer 3 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '16 hours'),
(gen_random_uuid(), 'acc-017', '7788990011', 'user-017', NULL, '5555555555', NULL, 1800000.00, 5000.00, 'External 3 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '3 days' + INTERVAL '14 hours'),

-- 4 DAYS AGO
(gen_random_uuid(), 'acc-019', '9900112233', 'user-019', 'acc-020', '0011223344', 'user-020', 4200000.00, 0.00, 'Chuyển tiền 4 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '11 hours'),
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', NULL, '6666666666', NULL, 950000.00, 5000.00, 'External 4 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '9 hours'),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', NULL, 'ATM-005', NULL, 1500000.00, 3000.00, 'Withdrawal 4 ngày trước', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '4 days' + INTERVAL '13 hours'),

-- 5 DAYS AGO
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', 'acc-006', '6789012345', 'user-006', 3800000.00, 0.00, 'Chuyển tiền 5 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '10 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-008', '8901234567', 'user-008', 8500000.00, 0.00, 'Deposit 5 ngày trước', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '15 hours'),
(gen_random_uuid(), 'acc-009', '9012345678', 'user-009', NULL, '7777777777', NULL, 2100000.00, 5000.00, 'External 5 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '12 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '12 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '12 hours'),

-- 6 DAYS AGO
(gen_random_uuid(), 'acc-011', '1122334455', 'user-011', 'acc-012', '2233445566', 'user-012', 1650000.00, 0.00, 'Transfer 6 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '14 hours'),
(gen_random_uuid(), 'acc-013', '3344556677', 'user-013', 'acc-014', '4455667788', 'user-014', 920000.00, 0.00, 'Chuyển tiền 6 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '16 hours'),
(gen_random_uuid(), 'acc-015', '5566778899', 'user-015', NULL, 'BILL-001', NULL, 380000.00, 2000.00, 'Bill payment 6 ngày trước', 'BILL_PAYMENT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '6 days' + INTERVAL '11 hours');


-- ==========================================
-- THIS MONTH DATA (more transactions)
-- ==========================================

-- Generate transactions for the past 15-30 days
INSERT INTO transactions (
    transaction_id,
    sender_account_id,
    sender_account_number,
    sender_user_id,
    receiver_account_id,
    receiver_account_number,
    receiver_user_id,
    amount,
    fee_amount,
    description,
    transaction_type,
    status,
    transfer_type,
    correlation_id,
    current_step,
    created_at,
    updated_at,
    completed_at
) VALUES
-- 10 DAYS AGO
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', 'acc-002', '2345678901', 'user-002', 2500000.00, 0.00, 'Chuyển tiền 10 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '10 hours'),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', NULL, '8888888888', NULL, 4500000.00, 5000.00, 'External 10 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '14 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-004', '4567890123', 'user-004', 12000000.00, 0.00, 'Deposit lớn 10 ngày trước', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '9 hours'),
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', NULL, 'ATM-006', NULL, 3000000.00, 3000.00, 'Withdrawal 10 ngày trước', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '10 days' + INTERVAL '16 hours'),

-- 15 DAYS AGO
(gen_random_uuid(), 'acc-007', '7890123456', 'user-007', 'acc-008', '8901234567', 'user-008', 1850000.00, 0.00, 'Chuyển tiền 15 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '11 hours'),
(gen_random_uuid(), 'acc-009', '9012345678', 'user-009', 'acc-010', '0123456789', 'user-010', 3200000.00, 0.00, 'Transfer 15 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '13 hours'),
(gen_random_uuid(), 'acc-011', '1122334455', 'user-011', NULL, '9999999999', NULL, 5500000.00, 5000.00, 'External 15 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '15 hours'),
(gen_random_uuid(), 'acc-013', '3344556677', 'user-013', NULL, 'BILL-002', NULL, 650000.00, 2000.00, 'Bill payment 15 ngày trước', 'BILL_PAYMENT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '15 days' + INTERVAL '10 hours'),

-- 20 DAYS AGO
(gen_random_uuid(), 'acc-015', '5566778899', 'user-015', 'acc-016', '6677889900', 'user-016', 2750000.00, 0.00, 'Chuyển tiền 20 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '12 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '12 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '12 hours'),
(gen_random_uuid(), 'acc-017', '7788990011', 'user-017', 'acc-018', '8899001122', 'user-018', 1920000.00, 0.00, 'Transfer 20 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '14 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '14 hours'),
(gen_random_uuid(), 'admin-system', 'SYSTEM', NULL, 'acc-019', '9900112233', 'user-019', 15000000.00, 0.00, 'Deposit rất lớn 20 ngày trước', 'DEPOSIT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '9 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '9 hours'),
(gen_random_uuid(), 'acc-020', '0011223344', 'user-020', NULL, '0000000000', NULL, 6800000.00, 5000.00, 'External 20 ngày trước', 'EXTERNAL_TRANSFER', 'SUCCESS', 'EXTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '16 hours', CURRENT_DATE - INTERVAL '20 days' + INTERVAL '16 hours'),

-- 25 DAYS AGO
(gen_random_uuid(), 'acc-001', '1234567890', 'user-001', 'acc-002', '2345678901', 'user-002', 890000.00, 0.00, 'Chuyển tiền 25 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '11 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '11 hours'),
(gen_random_uuid(), 'acc-003', '3456789012', 'user-003', 'acc-004', '4567890123', 'user-004', 4300000.00, 0.00, 'Transfer 25 ngày trước', 'INTERNAL_TRANSFER', 'SUCCESS', 'INTERNAL', 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '13 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '13 hours'),
(gen_random_uuid(), 'acc-005', '5678901234', 'user-005', NULL, 'ATM-007', NULL, 2200000.00, 3000.00, 'Withdrawal 25 ngày trước', 'WITHDRAWAL', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '15 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '15 hours'),
(gen_random_uuid(), 'acc-007', '7890123456', 'user-007', NULL, 'BILL-003', NULL, 520000.00, 2000.00, 'Bill payment 25 ngày trước', 'BILL_PAYMENT', 'SUCCESS', NULL, 'txn-' || gen_random_uuid(), 'COMPLETED', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '10 hours', CURRENT_DATE - INTERVAL '25 days' + INTERVAL '10 hours');

-- ==========================================
-- NOTE: Adjust account_id and account_number according to your actual data in account-service
-- ==========================================
