package com.uit.backupservice.dto;

import com.uit.backupservice.entity.BackupStatus;
import com.uit.backupservice.entity.BackupType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupResponse {

    private UUID backupId;
    private String backupName;
    private BackupType backupType;
    private BackupStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long totalSizeBytes;
    private String totalSizeFormatted;
    private String backupPath;
    private Boolean compressed;
    private Boolean encrypted;
    private String checksum;
    private String errorMessage;
    private String initiatedBy;
    private String cloudStorageUrl;
    private Boolean uploadedToCloud;
    private List<ServiceBackupDTO> serviceBackups;
    private Long durationMs;
    private LocalDateTime createdAt;
}
