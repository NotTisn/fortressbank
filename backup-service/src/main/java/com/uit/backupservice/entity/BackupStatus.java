package com.uit.backupservice.entity;

public enum BackupStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    VALIDATING,
    RESTORING
}
