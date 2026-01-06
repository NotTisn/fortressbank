package com.uit.backupservice.dto;

import com.uit.backupservice.entity.BackupStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBackupDTO {

    private UUID id;
    private String serviceName;
    private String databaseName;
    private String containerName;
    private String filePath;
    private Long fileSizeBytes;
    private String fileSizeFormatted;
    private Long recordCount;
    private String checksum;
    private BackupStatus status;
    private String errorMessage;
    private Long backupDurationMs;
}
