-- ============================================================================
-- NOTIFICATION SERVICE - INITIAL SCHEMA
-- ============================================================================
-- Version: V1
-- Description: Create tables for notifications and user preferences
-- Author: FortressBank Team
-- Date: 2025-12-14
-- ============================================================================

-- ============================================================================
-- TABLE: notifications
-- Description: Stores all notification messages sent to users
-- ============================================================================
CREATE TABLE notifications (
    notification_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    image VARCHAR(500),
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    sent_at TIMESTAMP NOT NULL,
    device_token VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Index for faster queries by user_id
CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- Index for filtering by type and read status
CREATE INDEX idx_notifications_type_read ON notifications(type, is_read);

-- Index for sorting by sent_at
CREATE INDEX idx_notifications_sent_at ON notifications(sent_at DESC);

-- ============================================================================
-- TABLE: user_preference
-- Description: Stores user notification preferences and contact information
-- ============================================================================
CREATE TABLE user_preference (
    user_id VARCHAR(36) PRIMARY KEY,
    phone_number VARCHAR(20),
    email VARCHAR(255),
    device_token VARCHAR(500),
    push_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_notification_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_notification_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

-- ============================================================================
-- TABLE: user_device_tokens
-- Description: Stores Firebase device tokens for push notifications
-- One user can have multiple devices (phones, tablets, etc.)
-- ============================================================================
-- CREATE TABLE user_device_tokens (
--     user_id VARCHAR(36) NOT NULL,
--     device_token VARCHAR(500) NOT NULL,
--     PRIMARY KEY (user_id, device_token),
--     CONSTRAINT fk_user_device_tokens_user_id
--         FOREIGN KEY (user_id)
--         REFERENCES user_preference(user_id)
--         ON DELETE CASCADE
-- );
--
-- -- Index for faster lookup by device token
-- CREATE INDEX idx_user_device_tokens_token ON user_device_tokens(device_token);