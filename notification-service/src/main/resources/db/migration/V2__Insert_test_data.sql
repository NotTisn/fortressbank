-- ============================================
-- Insert Test Data for Notification Service
-- ============================================

-- Test User 1: All notifications enabled (for comprehensive testing)
INSERT INTO user_preference (user_id, phone_number, email, device_token, push_notification_enabled, sms_notification_enabled, email_notification_enabled)
VALUES ('ACC001', '0946942439', 'quangtienngo661@gmail.com', 'fh6BHCTOTXeltzj1seXTwp:APA91bEfR8sJQmUSYpKYNQnkrp3viUMmS-v0Ry5ZKNjaVgfoMMsE4EjToI87N9r0n1vtqLWJ6LDT7Min-BeI0EJm5OmR3WwGQOkc0NgHJMHdsx91esHA_34' ,TRUE, TRUE, TRUE);

-- Test User 2: Only push notifications enabled
INSERT INTO user_preference (user_id, phone_number, email, device_token, push_notification_enabled, sms_notification_enabled, email_notification_enabled)
VALUES ('ACC002', '0912345678', 'user2@fortressbank.com', 'device-token-acc003-phone', TRUE, FALSE, FALSE);

-- Test User 3: Push and SMS enabled, email disabled
INSERT INTO user_preference (user_id, phone_number, email, device_token, push_notification_enabled, sms_notification_enabled, email_notification_enabled)
VALUES ('ACC003', '0987654321', 'user3@fortressbank.com', 'device-token-acc003-phone', TRUE, TRUE, FALSE);
