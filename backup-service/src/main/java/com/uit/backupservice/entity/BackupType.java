package com.uit.backupservice.entity;

public enum BackupType {
    FULL,           // All critical services
    INCREMENTAL,    // Only changed data (future feature)
    SINGLE_SERVICE, // One specific service
    CUSTOM          // User-selected services
}
